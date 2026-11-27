package com.kopibru.bookmanagement.security;

import com.kopibru.bookmanagement.controller.BookController;
import com.kopibru.bookmanagement.dto.BookDto;
import com.kopibru.bookmanagement.exception.GlobalExceptionHandler;
import com.kopibru.bookmanagement.model.BookStatus;
import com.kopibru.bookmanagement.service.BookCatalogService;
import com.kopibru.bookmanagement.service.BookRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
@Import({GlobalExceptionHandler.class, InternalApiKeyFilter.class})
@TestPropertySource(properties = "app.internal-api-key=test-internal-key")
class InternalApiKeyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookRegistrationService bookRegistrationService;

    @MockBean
    private BookCatalogService bookCatalogService;

    @Test
    void getBooks_rejectsMissingApiKey() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("IndexShelf")));
    }

    @Test
    void getBooks_rejectsWrongApiKey() throws Exception {
        mockMvc.perform(get("/api/books")
                        .header(InternalApiKeyFilter.HEADER_NAME, "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBooks_allowsValidApiKey() throws Exception {
        when(bookCatalogService.getBooks(anyString(), anyBoolean())).thenReturn(List.of(
                new BookDto("BOOK-001", "978-1", "Title", "Author", BookStatus.AVAILABLE, null)));

        mockMvc.perform(get("/api/books")
                        .header(InternalApiKeyFilter.HEADER_NAME, "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("BOOK-001"));
    }
}
