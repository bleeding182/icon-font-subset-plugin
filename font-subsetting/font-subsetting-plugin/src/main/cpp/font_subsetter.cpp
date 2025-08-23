#include "font_subsetter.h"
#include "logging.h"
#include "harfbuzz_wrappers.h"
#include <hb-ot.h>

hb_face_t* perform_subsetting(
    const FontData& font_data,
    const std::vector<unsigned int>& codepoints,
    const std::vector<AxisConfig>& axis_configs
) {
    // Create HarfBuzz blob from font data
    // Use READONLY mode for better performance
    HBBlob blob(hb_blob_create(
        font_data.data.data(),
        font_data.size,
        HB_MEMORY_MODE_READONLY,
        nullptr,
        nullptr
    ));
    
    if (!blob.get()) {
        log_error("Failed to create HarfBuzz blob");
        return nullptr;
    }
    
    // Create face from blob
    HBFace face(hb_face_create(blob, 0));
    if (!face.valid()) {
        log_error("Failed to create HarfBuzz face");
        return nullptr;
    }
    
    // Log font information
    unsigned int glyph_count = hb_face_get_glyph_count(face);
    log_info("Input font has " + std::to_string(glyph_count) + " glyphs");
    
    // Check for variable font axes
    unsigned int axis_count = hb_ot_var_get_axis_count(face);
    if (axis_count > 0) {
        log_info("Variable font with " + std::to_string(axis_count) + " axes");
        
        std::vector<hb_ot_var_axis_info_t> axes_info(axis_count);
        unsigned int axes_returned = axis_count;
        hb_ot_var_get_axis_infos(face, 0, &axes_returned, axes_info.data());
        
        for (unsigned int i = 0; i < axes_returned; i++) {
            char tag_str[5] = {0};
            tag_str[0] = (char)((axes_info[i].tag >> 24) & 0xFF);
            tag_str[1] = (char)((axes_info[i].tag >> 16) & 0xFF);
            tag_str[2] = (char)((axes_info[i].tag >> 8) & 0xFF);
            tag_str[3] = (char)(axes_info[i].tag & 0xFF);
            
            log_debug("  Axis " + std::string(tag_str) + ": " +
                     std::to_string(axes_info[i].min_value) + ".." +
                     std::to_string(axes_info[i].max_value) + 
                     " (default: " + std::to_string(axes_info[i].default_value) + ")");
        }
    }
    
    // Create subset input
    HBSubsetInput input(hb_subset_input_create_or_fail());
    if (!input.valid()) {
        log_error("Failed to create subset input");
        return nullptr;
    }
    
    // Add codepoints to subset
    hb_set_t* unicodes = hb_subset_input_unicode_set(input);
    for (unsigned int codepoint : codepoints) {
        hb_set_add(unicodes, codepoint);
    }
    log_info("Subsetting to " + std::to_string(codepoints.size()) + " codepoints");
    
    // Apply axis configurations
    if (!axis_configs.empty()) {
        log_info("Applying " + std::to_string(axis_configs.size()) + " axis configurations");
        
        for (const auto& axis : axis_configs) {
            if (axis.tag.length() != 4) {
                log_warn("Invalid axis tag (must be 4 characters): " + axis.tag);
                continue;
            }
            
            hb_tag_t tag = HB_TAG(axis.tag[0], axis.tag[1], axis.tag[2], axis.tag[3]);
            
            if (axis.remove) {
                hb_subset_input_pin_axis_to_default(input, face, tag);
                log_debug("  Removing axis: " + axis.tag);
            } else {
                hb_subset_input_set_axis_range(input, face, tag,
                                              axis.min_value, axis.max_value, axis.default_value);
                log_debug("  Configuring axis " + axis.tag + ": " +
                         std::to_string(axis.min_value) + ".." +
                         std::to_string(axis.max_value) +
                         " (default: " + std::to_string(axis.default_value) + ")");
            }
        }
    }
    
    // Perform the subset operation
    log_debug("Performing subset operation...");
    hb_face_t* subset_face = hb_subset_or_fail(face, input);
    
    if (!subset_face) {
        log_error("Subset operation failed");
        return nullptr;
    }
    
    // Log result information
    unsigned int subset_glyph_count = hb_face_get_glyph_count(subset_face);
    log_info("Output font has " + std::to_string(subset_glyph_count) + " glyphs");
    
    return subset_face;
}