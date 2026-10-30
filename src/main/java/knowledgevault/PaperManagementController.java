
package knowledgevault;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Controller
public class PaperManagementController {

    private final JdbcTemplate jdbcTemplate;

    public PaperManagementController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // GET ALL VENUES FOR THE FORM

    private List<Map<String, Object>> getVenues() {

        return jdbcTemplate.queryForList("""
            SELECT
                venue_id,
                venue_name,
                venue_type,
                publisher,
                venue_location
            FROM publication_venue
            ORDER BY venue_name
            """);
    }


    // VIEW AND SEARCH PAPERS

    @GetMapping("/paper-management")
    public String listPapers(
            @RequestParam(required = false, defaultValue = "") String search,
            Model model) {

        String searchTerm = search.trim();
        String pattern = "%" + searchTerm + "%";

        List<Map<String, Object>> papers =
            jdbcTemplate.queryForList("""
                SELECT
                    p.paper_id,
                    p.paper_title,
                    p.doi,
                    p.publication_date,
                    COALESCE(v.venue_name, 'Venue not specified')
                        AS venue_name
                FROM paper p
                LEFT JOIN publication_venue v
                    ON p.venue_id = v.venue_id
                WHERE CAST(p.paper_id AS CHAR) LIKE ?
                   OR p.paper_title LIKE ?
                   OR COALESCE(p.paper_abstract, '') LIKE ?
                   OR COALESCE(p.doi, '') LIKE ?
                   OR COALESCE(v.venue_name, '') LIKE ?
                ORDER BY p.paper_id DESC
                """,
                pattern, pattern, pattern, pattern, pattern
            );

        Long totalPapers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM paper",
            Long.class
        );

        model.addAttribute("papers", papers);
        model.addAttribute("searchTerm", searchTerm);
        model.addAttribute("totalPapers", totalPapers);

        return "paper-management";
    }


    // OPEN ADD FORM

    @GetMapping("/paper-management/new")
    public String addPaperForm(Model model) {

        model.addAttribute("formTitle", "Add Research Paper");
        model.addAttribute(
            "formAction",
            "/paper-management/add"
        );

        model.addAttribute("isEdit", false);
        model.addAttribute("paperId", "");
        model.addAttribute("paperTitle", "");
        model.addAttribute("paperAbstract", "");
        model.addAttribute("doi", "");
        model.addAttribute("publicationDate", "");
        model.addAttribute("selectedVenueId", "");

        model.addAttribute("venues", getVenues());

        return "paper-management-form";
    }


    // ADD NEW PAPER

    @PostMapping("/paper-management/add")
    public String addPaper(
            @RequestParam String paperTitle,
            @RequestParam(required = false) String paperAbstract,
            @RequestParam(required = false) String doi,
            @RequestParam(required = false) String publicationDate,
            @RequestParam(required = false) String venueId,
            RedirectAttributes redirectAttributes) {

        String title = paperTitle.trim();
        String abstractText = cleanOptional(paperAbstract);
        String doiValue = cleanOptional(doi);

        if (title.isBlank() || title.length() > 250) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Paper title is required and must not exceed 250 characters."
            );

            return "redirect:/paper-management/new";
        }

        if (doiValue != null && doiValue.length() > 255) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "DOI must not exceed 255 characters."
            );

            return "redirect:/paper-management/new";
        }

        try {

            java.sql.Date sqlDate = parseDate(publicationDate);
            Integer selectedVenue = parseVenueId(venueId);

            if (!isValidVenue(selectedVenue)) {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Please select a valid publication venue."
                );

                return "redirect:/paper-management/new";
            }

            jdbcTemplate.update(connection -> {

                PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO paper
                        (paper_title, paper_abstract, doi,
                         publication_date, venue_id)
                    VALUES (?, ?, ?, ?, ?)
                    """);

                ps.setString(1, title);

                setNullableString(ps, 2, abstractText);
                setNullableString(ps, 3, doiValue);
                setNullableDate(ps, 4, sqlDate);
                setNullableInteger(ps, 5, selectedVenue);

                return ps;
            });

            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Research paper added successfully."
            );

        } catch (DuplicateKeyException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This DOI already exists. Please enter a unique DOI."
            );

            return "redirect:/paper-management/new";

        } catch (DateTimeParseException | NumberFormatException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please enter a valid publication date and venue."
            );

            return "redirect:/paper-management/new";

        } catch (DataIntegrityViolationException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "The paper could not be saved because of a database constraint."
            );

            return "redirect:/paper-management/new";
        }

        return "redirect:/paper-management";
    }


    // OPEN EDIT FORM

    @GetMapping("/paper-management/edit/{id}")
    public String editPaperForm(
            @PathVariable int id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {

            Map<String, Object> paper =
                jdbcTemplate.queryForMap("""
                    SELECT
                        paper_id,
                        paper_title,
                        paper_abstract,
                        doi,
                        publication_date,
                        venue_id
                    FROM paper
                    WHERE paper_id = ?
                    """, id);

            model.addAttribute("formTitle", "Edit Research Paper");

            model.addAttribute(
                "formAction",
                "/paper-management/update/" + id
            );

            model.addAttribute("isEdit", true);
            model.addAttribute("paperId", paper.get("paper_id"));
            model.addAttribute("paperTitle", paper.get("paper_title"));
            model.addAttribute("paperAbstract", paper.get("paper_abstract"));
            model.addAttribute("doi", paper.get("doi"));

            Object date = paper.get("publication_date");

            model.addAttribute(
                "publicationDate",
                date == null ? "" : date.toString()
            );

            model.addAttribute(
                "selectedVenueId",
                paper.get("venue_id") == null
                    ? ""
                    : paper.get("venue_id").toString()
            );

            model.addAttribute("venues", getVenues());

            return "paper-management-form";

        } catch (EmptyResultDataAccessException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Research paper not found."
            );

            return "redirect:/paper-management";
        }
    }


    // UPDATE PAPER

    @PostMapping("/paper-management/update/{id}")
    public String updatePaper(
            @PathVariable int id,
            @RequestParam String paperTitle,
            @RequestParam(required = false) String paperAbstract,
            @RequestParam(required = false) String doi,
            @RequestParam(required = false) String publicationDate,
            @RequestParam(required = false) String venueId,
            RedirectAttributes redirectAttributes) {

        String title = paperTitle.trim();
        String abstractText = cleanOptional(paperAbstract);
        String doiValue = cleanOptional(doi);

        if (title.isBlank() || title.length() > 250) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Paper title is required and must not exceed 250 characters."
            );

            return "redirect:/paper-management/edit/" + id;
        }

        if (doiValue != null && doiValue.length() > 255) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "DOI must not exceed 255 characters."
            );

            return "redirect:/paper-management/edit/" + id;
        }

        try {

            java.sql.Date sqlDate = parseDate(publicationDate);
            Integer selectedVenue = parseVenueId(venueId);

            if (!isValidVenue(selectedVenue)) {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Please select a valid publication venue."
                );

                return "redirect:/paper-management/edit/" + id;
            }

            int updated = jdbcTemplate.update(connection -> {

                PreparedStatement ps = connection.prepareStatement("""
                    UPDATE paper
                    SET paper_title = ?,
                        paper_abstract = ?,
                        doi = ?,
                        publication_date = ?,
                        venue_id = ?
                    WHERE paper_id = ?
                    """);

                ps.setString(1, title);

                setNullableString(ps, 2, abstractText);
                setNullableString(ps, 3, doiValue);
                setNullableDate(ps, 4, sqlDate);
                setNullableInteger(ps, 5, selectedVenue);

                ps.setInt(6, id);

                return ps;
            });

            if (updated > 0) {

                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Research paper updated successfully."
                );

            } else {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Research paper not found."
                );
            }

        } catch (DuplicateKeyException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This DOI already belongs to another paper."
            );

            return "redirect:/paper-management/edit/" + id;

        } catch (DateTimeParseException | NumberFormatException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Please enter a valid publication date and venue."
            );

            return "redirect:/paper-management/edit/" + id;

        } catch (DataIntegrityViolationException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "The update could not be completed because of a database constraint."
            );

            return "redirect:/paper-management/edit/" + id;
        }

        return "redirect:/paper-management";
    }


    // DELETE PAPER SAFELY

    @PostMapping("/paper-management/delete/{id}")
    @Transactional
    public String deletePaper(
            @PathVariable int id,
            RedirectAttributes redirectAttributes) {

        try {

            Long linkedRecords = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM authorship
                     WHERE paper_id = ?) +
                    (SELECT COUNT(*) FROM paper_keyword
                     WHERE paper_id = ?) +
                    (SELECT COUNT(*) FROM paper_review
                     WHERE paper_id = ?) +
                    (SELECT COUNT(*) FROM citation
                     WHERE citing_paper_id = ?
                        OR cited_paper_id = ?)
                """,
                Long.class, id, id, id, id, id
            );

            if (linkedRecords != null && linkedRecords > 0) {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "This paper has authors, keywords, reviews or citation relationships. Deletion is blocked to protect research history."
                );

                return "redirect:/paper-management";
            }

            int deleted = jdbcTemplate.update(
                "DELETE FROM paper WHERE paper_id = ?",
                id
            );

            if (deleted > 0) {

                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Research paper deleted successfully."
                );

            } else {

                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Research paper not found."
                );
            }

        } catch (DataIntegrityViolationException e) {

            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "This paper is still referenced by another database record and cannot be deleted."
            );
        }

        return "redirect:/paper-management";
    }


    // HELPER METHODS

    private String cleanOptional(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }


    private java.sql.Date parseDate(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return java.sql.Date.valueOf(LocalDate.parse(value));
    }


    private Integer parseVenueId(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return Integer.valueOf(value);
    }


    private boolean isValidVenue(Integer venueId) {

        if (venueId == null) {
            return true;
        }

        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM publication_venue WHERE venue_id = ?",
            Long.class,
            venueId
        );

        return count != null && count > 0;
    }


    private void setNullableString(
            PreparedStatement ps,
            int position,
            String value) throws java.sql.SQLException {

        if (value == null) {
            ps.setNull(position, Types.VARCHAR);
        } else {
            ps.setString(position, value);
        }
    }


    private void setNullableDate(
            PreparedStatement ps,
            int position,
            java.sql.Date value) throws java.sql.SQLException {

        if (value == null) {
            ps.setNull(position, Types.DATE);
        } else {
            ps.setDate(position, value);
        }
    }


    private void setNullableInteger(
            PreparedStatement ps,
            int position,
            Integer value) throws java.sql.SQLException {

        if (value == null) {
            ps.setNull(position, Types.INTEGER);
        } else {
            ps.setInt(position, value);
        }
    }
}
