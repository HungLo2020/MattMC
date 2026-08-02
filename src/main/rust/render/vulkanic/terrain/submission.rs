use super::visibility::VisibleSection;

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct StaticTerrainSubmissionSummary {
    pub visible_sections: usize,
    pub omitted_sections: usize,
}

pub fn summarize_visible_sections(sections: &[VisibleSection]) -> StaticTerrainSubmissionSummary {
    StaticTerrainSubmissionSummary {
        visible_sections: sections.len(),
        omitted_sections: 0,
    }
}
