package com.schwab.agentic.shortener.repository;

import com.schwab.agentic.shortener.domain.ClickEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortCode(String shortCode);

    List<ClickEvent> findTop20ByShortCodeOrderByClickedAtDesc(String shortCode);
}
