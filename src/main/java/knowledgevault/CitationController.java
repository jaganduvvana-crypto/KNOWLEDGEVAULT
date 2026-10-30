
package knowledgevault;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class CitationController {

    private final JdbcTemplate jdbcTemplate;

    public CitationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/citations")
    public String showCitations(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Integer paperId,
            Model model) {

        String paperSql = """
            SELECT
                p.paper_id,
                p.paper_title,

                (
                    SELECT COUNT(*)
                    FROM citation c
                    WHERE c.cited_paper_id = p.paper_id
                ) AS incoming_citations,

                (
                    SELECT COUNT(*)
                    FROM citation c
                    WHERE c.citing_paper_id = p.paper_id
                ) AS outgoing_citations

            FROM paper p

            WHERE
                ? = ''
                OR p.paper_title LIKE CONCAT('%', ?, '%')

            ORDER BY incoming_citations DESC, p.paper_id
            """;

        List<Map<String, Object>> papers =
                jdbcTemplate.queryForList(paperSql, q, q);

        Integer totalCitationLinks =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM citation",
                    Integer.class
                );

        Integer totalPapers =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM paper",
                    Integer.class
                );

        Map<String, Object> selectedPaper = null;
        List<Map<String, Object>> relationships = List.of();

        Integer selectedPaperId = paperId;

        if (selectedPaperId != null) {

            String selectedSql = """
                SELECT paper_id, paper_title
                FROM paper
                WHERE paper_id = ?
                """;

            List<Map<String, Object>> selectedResults =
                    jdbcTemplate.queryForList(
                        selectedSql,
                        selectedPaperId
                    );

            if (selectedResults.isEmpty()) {
                selectedPaperId = null;
            } else {
                selectedPaper = selectedResults.get(0);
            }
        }

        if (selectedPaperId == null && !papers.isEmpty()) {

            selectedPaper = papers.get(0);

            selectedPaperId =
                    ((Number) selectedPaper.get("paper_id")).intValue();
        }

        if (selectedPaperId != null) {

            String relationshipSql = """
                SELECT
                    'CITES' AS relationship_type,
                    cited.paper_id AS related_paper_id,
                    cited.paper_title AS related_paper_title

                FROM citation c

                JOIN paper cited
                    ON c.cited_paper_id = cited.paper_id

                WHERE c.citing_paper_id = ?

                UNION ALL

                SELECT
                    'CITED BY' AS relationship_type,
                    citing.paper_id AS related_paper_id,
                    citing.paper_title AS related_paper_title

                FROM citation c

                JOIN paper citing
                    ON c.citing_paper_id = citing.paper_id

                WHERE c.cited_paper_id = ?

                ORDER BY relationship_type, related_paper_id
                """;

            relationships = jdbcTemplate.queryForList(
                    relationshipSql,
                    selectedPaperId,
                    selectedPaperId
            );
        }

        model.addAttribute("papers", papers);
        model.addAttribute("relationships", relationships);
        model.addAttribute("selectedPaper", selectedPaper);
        model.addAttribute("selectedPaperId", selectedPaperId);
        model.addAttribute("searchTerm", q);
        model.addAttribute("totalCitationLinks", totalCitationLinks);
        model.addAttribute("totalPapers", totalPapers);

        return "citations";
    }
}
