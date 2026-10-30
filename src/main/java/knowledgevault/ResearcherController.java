
package knowledgevault;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class ResearcherController {

    private final JdbcTemplate jdbcTemplate;

    public ResearcherController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/researchers")
    public String showResearchers(
            @RequestParam(defaultValue = "") String q,
            Model model) {

        String sql = """
            SELECT
                r.researcher_id,
                r.full_name,
                r.email,
                r.institution,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            ra.area_name
                            ORDER BY ra.area_name
                            SEPARATOR ', '
                        )
                        FROM researcher_area rsa
                        JOIN research_area ra
                            ON rsa.area_id = ra.area_id
                        WHERE rsa.researcher_id = r.researcher_id
                    ),
                    'No research areas assigned'
                ) AS research_areas,

                (
                    SELECT COUNT(DISTINCT a.paper_id)
                    FROM authorship a
                    WHERE a.researcher_id = r.researcher_id
                ) AS total_papers

            FROM researcher r

            WHERE
                ? = ''
                OR r.full_name LIKE CONCAT('%', ?, '%')
                OR r.institution LIKE CONCAT('%', ?, '%')
                OR EXISTS (
                    SELECT 1
                    FROM researcher_area rsa
                    JOIN research_area ra
                        ON rsa.area_id = ra.area_id
                    WHERE rsa.researcher_id = r.researcher_id
                    AND ra.area_name LIKE CONCAT('%', ?, '%')
                )

            ORDER BY r.full_name
            """;

        List<Map<String, Object>> researchers =
                jdbcTemplate.queryForList(sql, q, q, q, q);

        model.addAttribute("researchers", researchers);
        model.addAttribute("searchTerm", q);
        model.addAttribute("totalResearchers", researchers.size());

        return "researchers";
    }


    @GetMapping("/researchers/{id}")
    public String showResearcherDetails(
            @PathVariable int id,
            Model model) {

        String researcherSql = """
            SELECT
                r.researcher_id,
                r.full_name,
                r.email,
                r.institution,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            ra.area_name
                            ORDER BY ra.area_name
                            SEPARATOR ', '
                        )
                        FROM researcher_area rsa
                        JOIN research_area ra
                            ON rsa.area_id = ra.area_id
                        WHERE rsa.researcher_id = r.researcher_id
                    ),
                    'No research areas assigned'
                ) AS research_areas,

                (
                    SELECT COUNT(DISTINCT a.paper_id)
                    FROM authorship a
                    WHERE a.researcher_id = r.researcher_id
                ) AS total_papers,

                (
                    SELECT COUNT(*)
                    FROM project_member pm
                    WHERE pm.researcher_id = r.researcher_id
                ) AS total_projects,

                (
                    SELECT COUNT(*)
                    FROM paper_review pr
                    WHERE pr.researcher_id = r.researcher_id
                ) AS total_reviews

            FROM researcher r
            WHERE r.researcher_id = ?
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(researcherSql, id);

        if (results.isEmpty()) {
            return "redirect:/researchers";
        }

        Map<String, Object> researcher = results.get(0);


        List<Map<String, Object>> authoredPapers =
                jdbcTemplate.queryForList("""
                    SELECT
                        p.paper_id,
                        p.paper_title,
                        a.author_order,

                        COALESCE(
                            v.venue_name,
                            'Venue not specified'
                        ) AS venue_name,

                        DATE_FORMAT(
                            p.publication_date,
                            '%d %b %Y'
                        ) AS publication_date

                    FROM authorship a

                    JOIN paper p
                        ON a.paper_id = p.paper_id

                    LEFT JOIN publication_venue v
                        ON p.venue_id = v.venue_id

                    WHERE a.researcher_id = ?

                    ORDER BY p.publication_date DESC, p.paper_id
                    """, id);


        List<Map<String, Object>> projects =
                jdbcTemplate.queryForList("""
                    SELECT
                        p.project_id,
                        p.project_title,
                        pm.member_role,

                        DATE_FORMAT(
                            p.start_date,
                            '%d %b %Y'
                        ) AS start_date,

                        COALESCE(
                            DATE_FORMAT(
                                p.end_date,
                                '%d %b %Y'
                            ),
                            'Not specified'
                        ) AS end_date

                    FROM project_member pm

                    JOIN research_project p
                        ON pm.project_id = p.project_id

                    WHERE pm.researcher_id = ?

                    ORDER BY p.project_id
                    """, id);


        List<Map<String, Object>> reviews =
                jdbcTemplate.queryForList("""
                    SELECT
                        pr.review_id,
                        pr.rating,
                        COALESCE(
                            NULLIF(pr.review_text, ''),
                            'No written comment'
                        ) AS review_text,

                        DATE_FORMAT(
                            pr.review_date,
                            '%d %b %Y'
                        ) AS review_date,

                        p.paper_id,
                        p.paper_title

                    FROM paper_review pr

                    JOIN paper p
                        ON pr.paper_id = p.paper_id

                    WHERE pr.researcher_id = ?

                    ORDER BY pr.review_date DESC
                    """, id);


        model.addAttribute("researcher", researcher);
        model.addAttribute("authoredPapers", authoredPapers);
        model.addAttribute("projects", projects);
        model.addAttribute("reviews", reviews);

        return "researcher-details";
    }
}
