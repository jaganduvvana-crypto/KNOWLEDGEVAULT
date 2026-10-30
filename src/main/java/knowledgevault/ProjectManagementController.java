
package knowledgevault;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Controller
public class ProjectManagementController {

    private final JdbcTemplate jdbcTemplate;

    public ProjectManagementController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    // VIEW AND SEARCH PROJECTS

    @GetMapping("/project-management")
    public String listProjects(
            @RequestParam(required = false, defaultValue = "") String search,
            Model model) {

        String searchTerm = search.trim();
        String pattern = "%" + searchTerm + "%";

        List<Map<String, Object>> projects =
            jdbcTemplate.queryForList("""
                SELECT
                    rp.project_id,
                    rp.project_title,
                    rp.project_description,
                    rp.start_date,
                    rp.end_date,
                    (
                        SELECT COUNT(*)
                        FROM project_member pm
                        WHERE pm.project_id = rp.project_id
                    ) AS member_count
                FROM research_project rp
                WHERE CAST(rp.project_id AS CHAR) LIKE ?
                   OR rp.project_title LIKE ?
                   OR COALESCE(rp.project_description, '') LIKE ?
                   OR EXISTS (
                       SELECT 1
                       FROM project_member pm
                       JOIN researcher r
                           ON pm.researcher_id = r.researcher_id
                       WHERE pm.project_id = rp.project_id
                         AND (
                             r.full_name LIKE ?
                             OR r.institution LIKE ?
                         )
                   )
                ORDER BY rp.project_id DESC
                """,
                pattern, pattern, pattern, pattern, pattern
            );

        Long totalProjects = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM research_project",
            Long.class
        );

        model.addAttribute("projects", projects);
        model.addAttribute("searchTerm", searchTerm);
        model.addAttribute("totalProjects", totalProjects);

        return "project-management";
    }


    // OPEN ADD PROJECT FORM

    @GetMapping("/project-management/new")
    public String addProjectForm(Model model) {

        model.addAttribute("formTitle", "Add Research Project");
        model.addAttribute(
            "formAction",
            "/project-management/add"
        );

        model.addAttribute("isEdit", false);
        model.addAttribute("projectTitle", "");
        model.addAttribute("projectDescription", "");
        model.addAttribute("startDate", "");
        model.addAttribute("endDate", "");

        return "project-management-form";
    }


    // ADD NEW PROJECT

    @PostMapping("/project-management/add")
    public String addProject(
            @RequestParam String projectTitle,
            @RequestParam(required = false) String projectDescription,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            RedirectAttributes redirectAttributes) {

        String title = projectTitle.trim();
        String description = cleanOptional(projectDescription);

        if (title.isBlank() || title.length() > 200) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Project title is required and must not exceed 200 characters."
            );

            return "redirect:/project-management/new";
        }

        if (startDate == null || startDate.isBlank()) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Project start date is required."
            );

            return "redirect:/project-management/new";
        }

        try {

            LocalDate start = LocalDate.parse(startDate);

            LocalDate end = endDate == null || endDate.isBlank()
                ? null
                : LocalDate.parse(endDate);

            if (end != null && end.isBefore(start)) {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "End date cannot be earlier than start date."
                );

                return "redirect:/project-management/new";
            }

            jdbcTemplate.update(connection -> {

                PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO research_project
                        (project_title, project_description,
                         start_date, end_date)
                    VALUES (?, ?, ?, ?)
                    """);

                ps.setString(1, title);

                if (description == null) {
                    ps.setNull(2, Types.LONGVARCHAR);
                } else {
                    ps.setString(2, description);
                }

                ps.setDate(3, Date.valueOf(start));

                if (end == null) {
                    ps.setNull(4, Types.DATE);
                } else {
                    ps.setDate(4, Date.valueOf(end));
                }

                return ps;
            });

            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Research project added successfully."
            );

        } catch (DateTimeParseException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please enter valid project dates."
            );

            return "redirect:/project-management/new";

        } catch (DataIntegrityViolationException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Project could not be added because of a database constraint."
            );

            return "redirect:/project-management/new";
        }

        return "redirect:/project-management";
    }


    // OPEN EDIT PROJECT FORM

    @GetMapping("/project-management/edit/{id}")
    public String editProjectForm(
            @PathVariable int id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {

            Map<String, Object> project =
                jdbcTemplate.queryForMap("""
                    SELECT
                        project_id,
                        project_title,
                        project_description,
                        start_date,
                        end_date
                    FROM research_project
                    WHERE project_id = ?
                    """, id);

            model.addAttribute("formTitle", "Edit Research Project");

            model.addAttribute(
                "formAction",
                "/project-management/update/" + id
            );

            model.addAttribute("isEdit", true);
            model.addAttribute("projectTitle", project.get("project_title"));
            model.addAttribute(
                "projectDescription",
                project.get("project_description")
            );

            Object start = project.get("start_date");
            Object end = project.get("end_date");

            model.addAttribute(
                "startDate",
                start == null ? "" : start.toString()
            );

            model.addAttribute(
                "endDate",
                end == null ? "" : end.toString()
            );

            return "project-management-form";

        } catch (EmptyResultDataAccessException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Research project not found."
            );

            return "redirect:/project-management";
        }
    }


    // UPDATE PROJECT

    @PostMapping("/project-management/update/{id}")
    public String updateProject(
            @PathVariable int id,
            @RequestParam String projectTitle,
            @RequestParam(required = false) String projectDescription,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            RedirectAttributes redirectAttributes) {

        String title = projectTitle.trim();
        String description = cleanOptional(projectDescription);

        if (title.isBlank() || title.length() > 200) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Project title is required and must not exceed 200 characters."
            );

            return "redirect:/project-management/edit/" + id;
        }

        if (startDate == null || startDate.isBlank()) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Project start date is required."
            );

            return "redirect:/project-management/edit/" + id;
        }

        try {

            LocalDate start = LocalDate.parse(startDate);

            LocalDate end = endDate == null || endDate.isBlank()
                ? null
                : LocalDate.parse(endDate);

            if (end != null && end.isBefore(start)) {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "End date cannot be earlier than start date."
                );

                return "redirect:/project-management/edit/" + id;
            }

            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM research_project WHERE project_id = ?",
                Integer.class,
                id
            );

            if (exists == null || exists == 0) {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Research project not found."
                );

                return "redirect:/project-management";
            }

            jdbcTemplate.update(connection -> {

                PreparedStatement ps = connection.prepareStatement("""
                    UPDATE research_project
                    SET project_title = ?,
                        project_description = ?,
                        start_date = ?,
                        end_date = ?
                    WHERE project_id = ?
                    """);

                ps.setString(1, title);

                if (description == null) {
                    ps.setNull(2, Types.LONGVARCHAR);
                } else {
                    ps.setString(2, description);
                }

                ps.setDate(3, Date.valueOf(start));

                if (end == null) {
                    ps.setNull(4, Types.DATE);
                } else {
                    ps.setDate(4, Date.valueOf(end));
                }

                ps.setInt(5, id);

                return ps;
            });

            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Research project updated successfully."
            );

        } catch (DateTimeParseException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please enter valid project dates."
            );

            return "redirect:/project-management/edit/" + id;

        } catch (DataIntegrityViolationException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Project could not be updated because of a database constraint."
            );

            return "redirect:/project-management/edit/" + id;
        }

        return "redirect:/project-management";
    }


    // DELETE PROJECT SAFELY

    @PostMapping("/project-management/delete/{id}")
    @Transactional
    public String deleteProject(
            @PathVariable int id,
            RedirectAttributes redirectAttributes) {

        try {

            Long members = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM project_member
                WHERE project_id = ?
                """,
                Long.class,
                id
            );

            if (members != null && members > 0) {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "This project has existing team members. Deletion is blocked to protect project membership records."
                );

                return "redirect:/project-management";
            }

            int deleted = jdbcTemplate.update(
                "DELETE FROM research_project WHERE project_id = ?",
                id
            );

            if (deleted > 0) {

                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Research project deleted successfully."
                );

            } else {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Research project not found."
                );
            }

        } catch (DataIntegrityViolationException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This project is still referenced by another database record and cannot be deleted."
            );
        }

        return "redirect:/project-management";
    }


    // HELPER METHOD

    private String cleanOptional(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
