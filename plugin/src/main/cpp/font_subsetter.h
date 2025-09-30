#ifndef FONTSUBSETTING_FONT_SUBSETTER_H
#define FONTSUBSETTING_FONT_SUBSETTER_H

#include <string>
#include <vector>
#include <hb.h>
#include "font_io.h"

struct AxisConfig {
    std::string tag;
    float min_value;
    float max_value;
    float default_value;
    bool remove;
};

// Core font subsetting function
hb_face_t* perform_subsetting(
    const FontData& font_data,
    const std::vector<unsigned int>& codepoints,
    const std::vector<AxisConfig>& axis_configs,
    bool strip_hinting = true,
    bool strip_glyph_names = true
);

#endif // FONTSUBSETTING_FONT_SUBSETTER_H