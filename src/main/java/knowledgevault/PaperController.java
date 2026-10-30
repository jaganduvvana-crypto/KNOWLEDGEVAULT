
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
public class PaperController {

    private final JdbcTemplate jdbcTemplate;

    public PaperController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/papers")
    public String showPapers(
            @RequestParam(defaultValue = "") String q,
            Model model) {

        String sql = """
            SELECT
                p.paper_id,
                p.paper_title,
                COALESCE(
                    DATE_FORMAT(p.publication_date, '%d %b %Y'),
                    'Date not available'
                ) AS publication_date,
                COALESCE(v.venue_name, 'Venue not specified') AS venue_name,
                COALESCE(v.venue_type, '') AS venue_type,
                COALESCE(p.doi, 'Not provided') AS doi,
                COALESCE(
                    NULLIF(p.paper_abstract, ''),
                    'No abstract available.'
                ) AS paper_abstract,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            r.full_name
                            ORDER BY a.author_order
                            SEPARATOR ', '
                        )
                        FROM authorship a
                        JOIN researcher r
                            ON a.researcher_id = r.researcher_id
                        WHERE a.paper_id = p.paper_id
                    ),
                    'No authors listed'
                ) AS authors,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            k.keyword_text
                            ORDER BY k.keyword_text
                            SEPARATOR ', '
                        )
                        FROM paper_keyword pk
                        JOIN keyword k
                            ON pk.keyword_id = k.keyword_id
                        WHERE pk.paper_id = p.paper_id
                    ),
                    'No keywords assigned'
                ) AS keywords

            FROM paper p

            LEFT JOIN publication_venue v
                ON p.venue_id = v.venue_id

            WHERE
                ? = ''
                OR p.paper_title LIKE CONCAT('%', ?, '%')
                OR EXISTS (
                    SELECT 1
                    FROM paper_keyword pk
                    JOIN keyword k
                        ON pk.keyword_id = k.keyword_id
                    WHERE pk.paper_id = p.paper_id
                    AND k.keyword_text LIKE CONCAT('%', ?, '%')
                )

            ORDER BY p.paper_id DESC
            """;

        List<Map<String, Object>> papers =
                jdbcTemplate.queryForList(sql, q, q, q);

        model.addAttribute("papers", papers);
        model.addAttribute("searchTerm", q);
        model.addAttribute("totalPapers", papers.size());

        return "papers";
    }

    @GetMapping("/papers/{id}")
    public String showPaperDetails(
            @PathVariable int id,
            Model model) {

        String paperSql = """
            SELECT
                p.paper_id,
                p.paper_title,

                COALESCE(
                    NULLIF(p.paper_abstract, ''),
                    'No abstract available.'
                ) AS paper_abstract,

                COALESCE(p.doi, 'Not provided') AS doi,

                COALESCE(
                    DATE_FORMAT(p.publication_date, '%d %b %Y'),
                    'Date not available'
                ) AS publication_date,

                COALESCE(v.venue_name, 'Venue not specified')
                    AS venue_name,

                COALESCE(v.venue_type, 'Not specified')
                    AS venue_type,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            CONCAT(r.full_name, ' (Order ', a.author_order, ')')
                            ORDER BY a.author_order
                            SEPARATOR ', '
                        )
                        FROM authorship a
                        JOIN researcher r
                            ON a.researcher_id = r.researcher_id
                        WHERE a.paper_id = p.paper_id
                    ),
                    'No authors listed'
                ) AS authors,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            k.keyword_text
                            ORDER BY k.keyword_text
                            SEPARATOR ', '
                        )
                        FROM paper_keyword pk
                        JOIN keyword k
                            ON pk.keyword_id = k.keyword_id
                        WHERE pk.paper_id = p.paper_id
                    ),
                    'No keywords assigned'
                ) AS keywords,

                (
                    SELECT COUNT(*)
                    FROM citation c
                    WHERE c.cited_paper_id = p.paper_id
                ) AS incoming_citations,

                (
                    SELECT COUNT(*)
                    FROM citation c
                    WHERE c.citing_paper_id = p.paper_id
                ) AS outgoing_citations,

                (
                    SELECT COUNT(*)
                    FROM paper_review pr
                    WHERE pr.paper_id = p.paper_id
                ) AS total_reviews,

                COALESCE(
                    (
                        SELECT ROUND(AVG(pr.rating), 2)
                        FROM paper_review pr
                        WHERE pr.paper_id = p.paper_id
                    ),
                    0
                ) AS average_rating

            FROM paper p

            LEFT JOIN publication_venue v
                ON p.venue_id = v.venue_id

            WHERE p.paper_id = ?
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(paperSql, id);

        if (results.isEmpty()) {
            return "redirect:/papers";
        }

        Map<String, Object> paper = results.get(0);

        List<Map<String, Object>> citedPapers =
                jdbcTemplate.queryForList("""
                    SELECT
                        p.paper_id,
                        p.paper_title
                    FROM citation c
                    JOIN paper p
                        ON c.cited_paper_id = p.paper_id
                    WHERE c.citing_paper_id = ?
                    ORDER BY p.paper_id
                    """, id);

        List<Map<String, Object>> citingPapers =
                jdbcTemplate.queryForList("""
                    SELECT
                        p.paper_id,
                        p.paper_title
                    FROM citation c
                    JOIN paper p
                        ON c.citing_paper_id = p.paper_id
                    WHERE c.cited_paper_id = ?
                    ORDER BY p.paper_id
                    """, id);

        List<Map<String, Object>> reviews =
                jdbcTemplate.queryForList("""
                    SELECT
                        r.full_name AS reviewer_name,
                        pr.rating,
                        COALESCE(
                            NULLIF(pr.review_text, ''),
                            'No written comment'
                        ) AS review_text,
                        DATE_FORMAT(
                            pr.review_date,
                            '%d %b %Y'
                        ) AS review_date
                    FROM paper_review pr
                    JOIN researcher r
                        ON pr.researcher_id = r.researcher_id
                    WHERE pr.paper_id = ?
                    ORDER BY pr.review_date DESC
                    """, id);

        model.addAttribute("paper", paper);
        model.addAttribute("citedPapers", citedPapers);
        model.addAttribute("citingPapers", citingPapers);
        model.addAttribute("reviews", reviews);

        return "paper-details";
    }
}
