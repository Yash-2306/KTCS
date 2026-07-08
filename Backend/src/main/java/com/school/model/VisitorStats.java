package com.school.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "visitor_stats")
public class VisitorStats {

    @Id
    @Column(name = "page_name", length = 50)
    private String pageName;

    @Column(name = "visit_count")
    private Long visitCount;

    public VisitorStats() {}

    public VisitorStats(String pageName, Long visitCount) {
        this.pageName = pageName;
        this.visitCount = visitCount;
    }

    public String getPageName() {
        return pageName;
    }

    public void setPageName(String pageName) {
        this.pageName = pageName;
    }

    public Long getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(Long visitCount) {
        this.visitCount = visitCount;
    }
}
