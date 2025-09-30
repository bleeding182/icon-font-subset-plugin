#ifndef FONTSUBSETTING_FONT_METRICS_H
#define FONTSUBSETTING_FONT_METRICS_H

#include <string>
#include <vector>
#include <unordered_map>
#include <hb.h>

struct AxisInfo {
    std::string tag;
    float min_value;
    float max_value;
    float default_value;
};

struct FontMetrics {
    // Basic font info
    unsigned int glyph_count = 0;
    size_t total_size = 0;

    // Variable font axes
    std::vector<AxisInfo> axes;

    // Table sizes
    std::unordered_map<std::string, size_t> table_sizes;

    // Specific table tracking for hinting
    size_t fpgm_size = 0;
    size_t prep_size = 0;
    size_t cvt_size = 0;
    size_t post_size = 0;

    // Calculated metrics
    size_t total_hinting_size() const {
        return fpgm_size + prep_size + cvt_size;
    }

    size_t glyf_or_cff_size() const {
        auto glyf_it = table_sizes.find("glyf");
        if (glyf_it != table_sizes.end()) {
            return glyf_it->second;
        }
        auto cff_it = table_sizes.find("CFF ");
        if (cff_it != table_sizes.end()) {
            return cff_it->second;
        }
        auto cff2_it = table_sizes.find("CFF2");
        if (cff2_it != table_sizes.end()) {
            return cff2_it->second;
        }
        return 0;
    }
};

// Function to collect font metrics
FontMetrics collect_font_metrics(hb_face_t* face, const unsigned char* data = nullptr, size_t data_size = 0);

// Function to format table tag as string
std::string tag_to_string(hb_tag_t tag);

#endif // FONTSUBSETTING_FONT_METRICS_H