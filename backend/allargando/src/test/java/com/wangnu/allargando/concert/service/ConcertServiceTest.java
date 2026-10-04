package com.wangnu.allargando.concert.service;

import com.wangnu.allargando.concert.dto.ConcertResponseDTO;
import com.wangnu.allargando.concert.entity.Concert;
import com.wangnu.allargando.concert.repository.ConcertRepository;
import com.wangnu.allargando.global.exception.NotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @Mock ConcertRepository concertRepository;
    @InjectMocks ConcertService concertService;

    private Concert sampleConcert() {
        return Concert.fromSync("PF1", "제92회 코리아나 챔버 뮤직 소사이어티 정기연주회", "서양음악(클래식)",
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11),
                "예술의전당 [서울]", "서울특별시", "http://example.com/poster.jpg",
                "https://www.kopis.or.kr/por/db/pblprfr/pblprfrView.do?menuId=MNU_00020&mt20Id=PF1");
    }

    @Test
    void getConcertList_returnsMappedPage() {
        Page<Concert> page = new PageImpl<>(java.util.List.of(sampleConcert()), PageRequest.of(0, 10), 1);
        when(concertRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<ConcertResponseDTO> result = concertService.getConcertList(
                null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("제92회 코리아나 챔버 뮤직 소사이어티 정기연주회");
    }

    @Test
    void getConcert_returnsDtoWhenFound() {
        when(concertRepository.findById(1L)).thenReturn(Optional.of(sampleConcert()));

        ConcertResponseDTO result = concertService.getConcert(1L);

        assertThat(result.getVenue()).isEqualTo("예술의전당 [서울]");
        assertThat(result.getSourceUrl()).contains("mt20Id=PF1");
    }

    @Test
    void getConcert_throwsNotFoundWhenMissing() {
        when(concertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> concertService.getConcert(999L))
                .isInstanceOf(NotFoundException.class);
    }
}
