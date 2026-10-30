
package knowledgevault;

import org.springframework.dao.DuplicateKeyException;
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

import java.util.List;
import java.util.Map;

@Controller
public class ResearcherManagementController {

    private final JdbcTemplate jdbcTemplate;

    public ResearcherManagementController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // VIEW AND SEARCH RESEARCHERS

    @GetMapping("/researcher-management")
    public String listResearchers(
            @RequestParam(required = false, defaultValue = "") String search,
            Model model) {

        String searchTerm = search.trim();
        String pattern = "%" + searchTerm + "%";

        List<Map<String, Object>> researchers =
            jdbcTemplate.queryForList("""
                SELECT
                    researcher_id,
                    full_name,
                    email,
                    institution,
                    created_at
                FROM researcher
                WHERE CAST(researcher_id AS CHAR) LIKE ?
                   OR full_name LIKE ?
                   OR email LIKE ?
                   OR institution LIKE ?
                ORDER BY researcher_id DESC
                """,
                pattern, pattern, pattern, pattern
            );

        Long totalResearchers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM researcher",
            Long.class
        );

        model.addAttribute("researchers", researchers);
        model.addAttribute("searchTerm", searchTerm);
        model.addAttribute("totalResearchers", totalResearchers);

        return "researcher-management";
    }


    // OPEN ADD FORM

    @GetMapping("/researcher-management/new")
    public String addResearcherForm(Model model) {

        model.addAttribute("formTitle", "Add Researcher");
        model.addAttribute("formAction", "/researcher-management/add");
        model.addAttribute("isEdit", false);

        model.addAttribute("researcherId", "");
        model.addAttribute("fullName", "");
        model.addAttribute("email", "");
        model.addAttribute("institution", "");

        return "researcher-management-form";
    }


    // ADD RESEARCHER

    @PostMapping("/researcher-management/add")
    public String addResearcher(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String institution,
            RedirectAttributes redirectAttributes) {

        if (fullName.isBlank() || email.isBlank()
                || institution.isBlank()) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please fill in all required fields."
            );

            return "redirect:/researcher-management/new";
        }

        try {

            jdbcTemplate.update("""
                INSERT INTO researcher
                    (full_name, email, institution)
                VALUES (?, ?, ?)
                """,
                fullName.trim(),
                email.trim(),
                institution.trim()
            );

            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Researcher added successfully."
            );

        } catch (DuplicateKeyException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This email address already exists."
            );

            return "redirect:/researcher-management/new";
        }

        return "redirect:/researcher-management";
    }


    // OPEN EDIT FORM

    @GetMapping("/researcher-management/edit/{id}")
    public String editResearcherForm(
            @PathVariable int id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {

            Map<String, Object> researcher =
                jdbcTemplate.queryForMap("""
                    SELECT
                        researcher_id,
                        full_name,
                        email,
                        institution
                    FROM researcher
                    WHERE researcher_id = ?
                    """, id);

            model.addAttribute("formTitle", "Edit Researcher");

            model.addAttribute(
                "formAction",
                "/researcher-management/update/" + id
            );

            model.addAttribute("isEdit", true);
            model.addAttribute("researcherId", researcher.get("researcher_id"));
            model.addAttribute("fullName", researcher.get("full_name"));
            model.addAttribute("email", researcher.get("email"));
            model.addAttribute("institution", researcher.get("institution"));

            return "researcher-management-form";

        } catch (EmptyResultDataAccessException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Researcher not found."
            );

            return "redirect:/researcher-management";
        }
    }


    // UPDATE RESEARCHER

    @PostMapping("/researcher-management/update/{id}")
    public String updateResearcher(
            @PathVariable int id,
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String institution,
            RedirectAttributes redirectAttributes) {

        if (fullName.isBlank() || email.isBlank()
                || institution.isBlank()) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please fill in all required fields."
            );

            return "redirect:/researcher-management/edit/" + id;
        }

        try {

            int updated = jdbcTemplate.update("""
                UPDATE researcher
                SET full_name = ?,
                    email = ?,
                    institution = ?
                WHERE researcher_id = ?
                """,
                fullName.trim(),
                email.trim(),
                institution.trim(),
                id
            );

            if (updated > 0) {

                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Researcher updated successfully."
                );

            } else {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Researcher not found."
                );
            }

        } catch (DuplicateKeyException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This email address is already used by another researcher."
            );

            return "redirect:/researcher-management/edit/" + id;
        }

        return "redirect:/researcher-management";
    }


    // DELETE RESEARCHER

    @PostMapping("/researcher-management/delete/{id}")
    @Transactional
    public String deleteResearcher(
            @PathVariable int id,
            RedirectAttributes redirectAttributes) {

        try {

            Long linkedRecords = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM researcher_area
                     WHERE researcher_id = ?) +
                    (SELECT COUNT(*) FROM project_member
                     WHERE researcher_id = ?) +
                    (SELECT COUNT(*) FROM authorship
                     WHERE researcher_id = ?) +
                    (SELECT COUNT(*) FROM paper_review
                     WHERE researcher_id = ?)
                """,
                Long.class, id, id, id, id
            );

            if (linkedRecords != null && linkedRecords > 0) {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Cannot delete this researcher because they have linked research records. Existing research history has been protected."
                );

                return "redirect:/researcher-management";
            }

            int deleted = jdbcTemplate.update(
                "DELETE FROM researcher WHERE researcher_id = ?",
                id
            );

            if (deleted > 0) {

                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Researcher deleted successfully."
                );

            } else {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Researcher not found."
                );
            }

        } catch (DataIntegrityViolationException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This researcher is still referenced by another database record and cannot be deleted."
            );
        }

        return "redirect:/researcher-management";
    }
}
