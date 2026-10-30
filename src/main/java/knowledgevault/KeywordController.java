
package knowledgevault;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class KeywordController {

    private final JdbcTemplate jdbcTemplate;

    public KeywordController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/keywords")
    public String showKeywords(
            @RequestParam(defaultValue = "") String q,
            Model model) {

        String sql = """
            SELECT
                k.keyword_id,
                k.keyword_text,

                COUNT(DISTINCT pk.paper_id) AS total_papers,

                COALESCE(
                    GROUP_CONCAT(
                        DISTINCT CONCAT(
                            p.paper_id,
                            ': ',
                            p.paper_title
                        )
                        ORDER BY p.paper_id
                        SEPARATOR ' | '
                    ),
                    'No papers assigned'
                ) AS related_papers

            FROM keyword k

            LEFT JOIN paper_keyword pk
                ON k.keyword_id = pk.keyword_id

            LEFT JOIN paper p
                ON pk.paper_id = p.paper_id

            WHERE
                ? = ''
                OR k.keyword_text LIKE CONCAT('%', ?, '%')
                OR EXISTS (
                    SELECT 1
                    FROM paper_keyword pk2
                    JOIN paper p2
                        ON pk2.paper_id = p2.paper_id
                    WHERE pk2.keyword_id = k.keyword_id
                    AND p2.paper_title LIKE CONCAT('%', ?, '%')
                )

            GROUP BY
                k.keyword_id,
                k.keyword_text

            ORDER BY k.keyword_text
            """;

        List<Map<String, Object>> keywords =
                jdbcTemplate.queryForList(sql, q, q, q);

        Integer totalKeywords =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM keyword",
                    Integer.class
                );

        Integer totalRelationships =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM paper_keyword",
                    Integer.class
                );

        Integer matchingPapers =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(DISTINCT pk.paper_id)
                    FROM paper_keyword pk
                    JOIN keyword k
                        ON pk.keyword_id = k.keyword_id
                    WHERE
                        ? = ''
                        OR k.keyword_text LIKE CONCAT('%', ?, '%')
                    """,
                    Integer.class,
                    q, q
                );

        model.addAttribute("keywords", keywords);
        model.addAttribute("searchTerm", q);
        model.addAttribute("totalKeywords", totalKeywords);
        model.addAttribute("totalRelationships", totalRelationships);
        model.addAttribute("matchingPapers", matchingPapers);

        return "keywords";
    }
}
