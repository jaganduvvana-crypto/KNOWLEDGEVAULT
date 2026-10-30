
package knowledgevault;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class ReviewController {

    private final JdbcTemplate jdbcTemplate;

    public ReviewController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/reviews")
    public String showReviews(
            @RequestParam(defaultValue = "") String q,
            Model model) {

        String sql = """
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
                p.paper_title,

                r.researcher_id,
                r.full_name AS reviewer_name

            FROM paper_review pr

            JOIN paper p
                ON pr.paper_id = p.paper_id

            JOIN researcher r
                ON pr.researcher_id = r.researcher_id

            WHERE
                ? = ''
                OR p.paper_title LIKE CONCAT('%', ?, '%')
                OR r.full_name LIKE CONCAT('%', ?, '%')
                OR COALESCE(pr.review_text, '') LIKE CONCAT('%', ?, '%')

            ORDER BY
                pr.review_date DESC,
                pr.review_id DESC
            """;

        List<Map<String, Object>> reviews =
                jdbcTemplate.queryForList(sql, q, q, q, q);

        Map<String, Object> statistics =
                jdbcTemplate.queryForMap("""
                    SELECT
                        COUNT(*) AS total_reviews,
                        COUNT(DISTINCT paper_id) AS reviewed_papers,
                        ROUND(AVG(rating), 2) AS average_rating
                    FROM paper_review
                    """);

        model.addAttribute("reviews", reviews);
        model.addAttribute("statistics", statistics);
        model.addAttribute("searchTerm", q);
        model.addAttribute("totalReviews", reviews.size());

        return "reviews";
    }
}
