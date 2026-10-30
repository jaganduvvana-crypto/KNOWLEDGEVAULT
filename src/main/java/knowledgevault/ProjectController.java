
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
public class ProjectController {

    private final JdbcTemplate jdbcTemplate;

    public ProjectController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/projects")
    public String showProjects(
            @RequestParam(defaultValue = "") String q,
            Model model) {

        String sql = """
            SELECT
                p.project_id,
                p.project_title,

                COALESCE(
                    p.project_description,
                    'No project description available'
                ) AS project_description,

                DATE_FORMAT(
                    p.start_date, '%d %b %Y'
                ) AS start_date,

                COALESCE(
                    DATE_FORMAT(p.end_date, '%d %b %Y'),
                    'Not specified'
                ) AS end_date,

                CASE
                    WHEN p.start_date > CURRENT_DATE()
                        THEN 'UPCOMING'
                    WHEN p.end_date IS NULL
                         OR p.end_date >= CURRENT_DATE()
                        THEN 'ONGOING'
                    ELSE 'COMPLETED'
                END AS project_status,

                COALESCE(
                    (
                        SELECT GROUP_CONCAT(
                            CONCAT(
                                r.full_name,
                                ' (',
                                COALESCE(pm.member_role, 'Member'),
                                ')'
                            )
                            ORDER BY r.full_name
                            SEPARATOR ', '
                        )
                        FROM project_member pm
                        JOIN researcher r
                            ON pm.researcher_id = r.researcher_id
                        WHERE pm.project_id = p.project_id
                    ),
                    'No members assigned'
                ) AS team_members,

                (
                    SELECT COUNT(DISTINCT pm.researcher_id)
                    FROM project_member pm
                    WHERE pm.project_id = p.project_id
                ) AS total_members

            FROM research_project p

            WHERE
                ? = ''
                OR p.project_title LIKE CONCAT('%', ?, '%')
                OR COALESCE(p.project_description, '') LIKE CONCAT('%', ?, '%')
                OR EXISTS (
                    SELECT 1
                    FROM project_member pm
                    JOIN researcher r
                        ON pm.researcher_id = r.researcher_id
                    WHERE pm.project_id = p.project_id
                    AND r.full_name LIKE CONCAT('%', ?, '%')
                )

            ORDER BY p.project_id
            """;

        List<Map<String, Object>> projects =
                jdbcTemplate.queryForList(sql, q, q, q, q);

        model.addAttribute("projects", projects);
        model.addAttribute("searchTerm", q);
        model.addAttribute("totalProjects", projects.size());

        return "projects";
    }


    @GetMapping("/projects/{id}")
    public String showProjectDetails(
            @PathVariable int id,
            Model model) {

        // PROJECT INFORMATION

        String projectSql = """
            SELECT
                p.project_id,
                p.project_title,

                COALESCE(
                    p.project_description,
                    'No project description available'
                ) AS project_description,

                DATE_FORMAT(
                    p.start_date, '%d %b %Y'
                ) AS start_date,

                COALESCE(
                    DATE_FORMAT(p.end_date, '%d %b %Y'),
                    'Not specified'
                ) AS end_date,

                CASE
                    WHEN p.start_date > CURRENT_DATE()
                        THEN 'UPCOMING'
                    WHEN p.end_date IS NULL
                         OR p.end_date >= CURRENT_DATE()
                        THEN 'ONGOING'
                    ELSE 'COMPLETED'
                END AS project_status,

                (
                    SELECT COUNT(DISTINCT pm.researcher_id)
                    FROM project_member pm
                    WHERE pm.project_id = p.project_id
                ) AS total_members,

                (
                    SELECT COUNT(DISTINCT a.paper_id)
                    FROM project_member pm
                    JOIN authorship a
                        ON pm.researcher_id = a.researcher_id
                    WHERE pm.project_id = p.project_id
                ) AS related_papers

            FROM research_project p
            WHERE p.project_id = ?
            """;

        List<Map<String, Object>> projectResults =
                jdbcTemplate.queryForList(projectSql, id);

        if (projectResults.isEmpty()) {
            return "redirect:/projects";
        }

        Map<String, Object> project = projectResults.get(0);


        // PROJECT TEAM MEMBERS

        String memberSql = """
            SELECT
                r.researcher_id,
                r.full_name,
                r.email,
                r.institution,

                COALESCE(
                    pm.member_role,
                    'Member'
                ) AS member_role,

                (
                    SELECT COUNT(DISTINCT a.paper_id)
                    FROM authorship a
                    WHERE a.researcher_id = r.researcher_id
                ) AS authored_papers

            FROM project_member pm

            JOIN researcher r
                ON pm.researcher_id = r.researcher_id

            WHERE pm.project_id = ?

            ORDER BY r.full_name
            """;

        List<Map<String, Object>> members =
                jdbcTemplate.queryForList(memberSql, id);


        // PROJECT PUBLICATIONS
        // FIX: publication_date is included in SELECT
        // so MySQL permits ORDER BY with DISTINCT.

        String publicationSql = """
            SELECT DISTINCT
                p.paper_id,
                p.paper_title,

                COALESCE(
                    v.venue_name,
                    'Venue not specified'
                ) AS venue_name,

                DATE_FORMAT(
                    p.publication_date,
                    '%d %b %Y'
                ) AS publication_date,

                p.publication_date AS publication_sort

            FROM project_member pm

            JOIN authorship a
                ON pm.researcher_id = a.researcher_id

            JOIN paper p
                ON a.paper_id = p.paper_id

            LEFT JOIN publication_venue v
                ON p.venue_id = v.venue_id

            WHERE pm.project_id = ?

            ORDER BY publication_sort DESC, p.paper_id
            """;

        List<Map<String, Object>> publications =
                jdbcTemplate.queryForList(publicationSql, id);


        model.addAttribute("project", project);
        model.addAttribute("members", members);
        model.addAttribute("publications", publications);

        return "project-details";
    }
}
