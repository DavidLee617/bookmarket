package com.example.demo.service;

import com.example.demo.dto.BookRequest;
import com.example.demo.entity.Book;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    public Book findById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("图书不存在: id=" + id));
    }

    @Transactional
    public Book create(BookRequest request) {
        Book book = new Book();
        apply(book, request);
        return bookRepository.save(book);
    }

    @Transactional
    public Book update(Long id, BookRequest request) {
        Book book = findById(id);
        apply(book, request);
        return bookRepository.save(book);
    }

    @Transactional
    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw BusinessException.notFound("图书不存在: id=" + id);
        }
        bookRepository.deleteById(id);
    }

    private void apply(Book book, BookRequest request) {
        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setIsbn(request.getIsbn());
        book.setPrice(request.getPrice());
        book.setStock(request.getStock());
    }

}
