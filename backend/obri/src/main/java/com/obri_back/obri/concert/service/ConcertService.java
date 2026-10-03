package com.obri_back.obri.concert.service;

import com.obri_back.obri.concert.dto.ConcertResponseDTO;
import com.obri_back.obri.concert.entity.Concert;
import com.obri_back.obri.concert.repository.ConcertRepository;
import com.obri_back.obri.concert.repository.ConcertSpecification;
import com.obri_back.obri.global.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/*
 * 연주회 관련 비즈니스 로직
 * 조회(전체/단건) 전용 — 데이터는 KopisSyncService가 채움
 */
@Service
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertRepository concertRepository;

    // 연주회 전체 조회 — Specification 동적 필터 적용 후 DTO로 반환
    @Transactional(readOnly = true)
    public Page<ConcertResponseDTO> getConcertList(List<String> categories, List<String> regions,
            LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Specification<Concert> spec = ConcertSpecification.filter(categories, regions, fromDate, toDate);
        return concertRepository.findAll(spec, pageable).map(ConcertResponseDTO::from);
    }

    // 연주회 단건 조회
    @Transactional(readOnly = true)
    public ConcertResponseDTO getConcert(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new NotFoundException("연주회를 찾을 수 없습니다"));
        return ConcertResponseDTO.from(concert);
    }
}
