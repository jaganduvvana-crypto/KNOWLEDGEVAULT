
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
public class KeywordManagementController {

    private final JdbcTemplate jdbcTemplate;

    public KeywordManagementController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    // VIEW AND SEARCH KEYWORDS

    @GetMapping("/keyword-management")
    public String listKeywords(
            @RequestParam(required = false, defaultValue = "") String search,
            Model model) {

        String searchTerm = search.trim();
        String pattern = "%" + searchTerm + "%";

        List<Map<String, Object>> keywords =
            jdbcTemplate.queryForList("""
                SELECT
                    k.keyword_id,
                    k.keyword_text,
                    COUNT(pk.paper_id) AS paper_count
                FROM keyword k
                LEFT JOIN paper_keyword pk
                    ON k.keyword_id = pk.keyword_id
                WHERE CAST(k.keyword_id AS CHAR) LIKE ?
                   OR k.keyword_text LIKE ?
                GROUP BY k.keyword_id, k.keyword_text
                ORDER BY k.keyword_id DESC
                """,
                pattern, pattern
            );

        Long totalKeywords = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM keyword",
            Long.class
        );

        model.addAttribute("keywords", keywords);
        model.addAttribute("searchTerm", searchTerm);
        model.addAttribute("totalKeywords", totalKeywords);

        return "keyword-management";
    }


    // OPEN ADD KEYWORD FORM

    @GetMapping("/keyword-management/new")
    public String addKeywordForm(Model model) {

        model.addAttribute("formTitle", "Add Keyword");
        model.addAttribute(
            "formAction",
            "/keyword-management/add"
        );

        model.addAttribute("isEdit", false);
        model.addAttribute("keywordText", "");

        return "keyword-management-form";
    }


    // ADD NEW KEYWORD

    @PostMapping("/keyword-management/add")
    public String addKeyword(
            @RequestParam String keywordText,
            RedirectAttributes redirectAttributes) {

        String text = keywordText.trim();

        if (text.isBlank() || text.length() > 100) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Keyword is required and must not exceed 100 characters."
            );

            return "redirect:/keyword-management/new";
        }

        try {

            jdbcTemplate.update("""
                INSERT INTO keyword (keyword_text)
                VALUES (?)
                """,
                text
            );

            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Keyword added successfully."
            );

        } catch (DuplicateKeyException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This keyword already exists."
            );

            return "redirect:/keyword-management/new";
        }

        return "redirect:/keyword-management";
    }


    // OPEN EDIT KEYWORD FORM

    @GetMapping("/keyword-management/edit/{id}")
    public String editKeywordForm(
            @PathVariable int id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {

            Map<String, Object> keyword =
                jdbcTemplate.queryForMap("""
                    SELECT keyword_id, keyword_text
                    FROM keyword
                    WHERE keyword_id = ?
                    """,
                    id
                );

            model.addAttribute("formTitle", "Edit Keyword");

            model.addAttribute(
                "formAction",
                "/keyword-management/update/" + id
            );

            model.addAttribute("isEdit", true);
            model.addAttribute("keywordText", keyword.get("keyword_text"));

            return "keyword-management-form";

        } catch (EmptyResultDataAccessException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Keyword not found."
            );

            return "redirect:/keyword-management";
        }
    }


    // UPDATE KEYWORD

    @PostMapping("/keyword-management/update/{id}")
    public String updateKeyword(
            @PathVariable int id,
            @RequestParam String keywordText,
            RedirectAttributes redirectAttributes) {

        String text = keywordText.trim();

        if (text.isBlank() || text.length() > 100) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Keyword is required and must not exceed 100 characters."
            );

            return "redirect:/keyword-management/edit/" + id;
        }

        try {

            int updated = jdbcTemplate.update("""
                UPDATE keyword
                SET keyword_text = ?
                WHERE keyword_id = ?
                """,
                text, id
            );

            if (updated > 0) {

                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Keyword updated successfully."
                );

            } else {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Keyword not found."
                );
            }

        } catch (DuplicateKeyException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This keyword already exists."
            );

            return "redirect:/keyword-management/edit/" + id;
        }

        return "redirect:/keyword-management";
    }


    // DELETE KEYWORD SAFELY

    @PostMapping("/keyword-management/delete/{id}")
    @Transactional
    public String deleteKeyword(
            @PathVariable int id,
            RedirectAttributes redirectAttributes) {

        try {

            Long linkedPapers = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM paper_keyword
                WHERE keyword_id = ?
                """,
                Long.class,
                id
            );

            if (linkedPapers != null && linkedPapers > 0) {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "This keyword is associated with research papers. Deletion is blocked to protect existing paper-keyword relationships."
                );

                return "redirect:/keyword-management";
            }

            int deleted = jdbcTemplate.update(
                "DELETE FROM keyword WHERE keyword_id = ?",
                id
            );

            if (deleted > 0) {

                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Keyword deleted successfully."
                );

            } else {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Keyword not found."
                );
            }

        } catch (DataIntegrityViolationException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This keyword is still referenced by another database record and cannot be deleted."
            );
        }

        return "redirect:/keyword-management";
    }
}
