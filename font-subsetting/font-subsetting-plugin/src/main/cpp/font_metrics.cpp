#include "font_metrics.h"
#include "logging.h"
#include <hb-ot.h>

std::string tag_to_string(hb_tag_t tag) {
    char tag_str[5] = {0};
    tag_str[0] = (char)((tag >> 24) & 0xFF);
    tag_str[1] = (char)((tag >> 16) & 0xFF);
    tag_str[2] = (char)((tag >> 8) & 0xFF);
    tag_str[3] = (char)(tag & 0xFF);
    return std::string(tag_str);
}

FontMetrics collect_font_metrics(hb_face_t* face, const unsigned char* data, size_t data_size) {
    FontMetrics metrics;

    if (!face) {
        log_error("Invalid face provided to collect_font_metrics");
        return metrics;
    }

    // Basic font info
    metrics.glyph_count = hb_face_get_glyph_count(face);
    metrics.total_size = data_size;

    // Collect variable font axes
    unsigned int axis_count = hb_ot_var_get_axis_count(face);
    if (axis_count > 0) {
        std::vector<hb_ot_var_axis_info_t> axes_info(axis_count);
        unsigned int axes_returned = axis_count;
        hb_ot_var_get_axis_infos(face, 0, &axes_returned, axes_info.data());

        for (unsigned int i = 0; i < axes_returned; i++) {
            AxisInfo axis;
            axis.tag = tag_to_string(axes_info[i].tag);
            axis.min_value = axes_info[i].min_value;
            axis.max_value = axes_info[i].max_value;
            axis.default_value = axes_info[i].default_value;
            metrics.axes.push_back(axis);
        }
    }

    // Collect table sizes
    unsigned int table_count = 0;
    const unsigned int max_tables = 128; // Reasonable max for fonts
    hb_tag_t table_tags[max_tables];

    // Get all table tags
    hb_face_get_table_tags(face, 0, &table_count, table_tags);
    if (table_count > max_tables) {
        table_count = max_tables;
    }

    // Get size of each table
    for (unsigned int i = 0; i < table_count; i++) {
        hb_blob_t* blob = hb_face_reference_table(face, table_tags[i]);
        if (blob) {
            unsigned int blob_length = hb_blob_get_length(blob);
            std::string tag_str = tag_to_string(table_tags[i]);
            metrics.table_sizes[tag_str] = blob_length;

            // Track specific tables for detailed reporting
            if (tag_str == "fpgm") {
                metrics.fpgm_size = blob_length;
            } else if (tag_str == "prep") {
                metrics.prep_size = blob_length;
            } else if (tag_str == "cvt ") {
                metrics.cvt_size = blob_length;
            } else if (tag_str == "post") {
                metrics.post_size = blob_length;
            }

            hb_blob_destroy(blob);
        }
    }

    // Log summary
    log_debug("Font metrics collected: " + std::to_string(metrics.glyph_count) + " glyphs, " +
              std::to_string(table_count) + " tables, " +
              std::to_string(metrics.axes.size()) + " axes");

    return metrics;
}