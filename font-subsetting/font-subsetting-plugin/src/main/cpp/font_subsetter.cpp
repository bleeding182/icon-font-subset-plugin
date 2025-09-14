#include "font_subsetter.h"
#include "logging.h"
#include "harfbuzz_wrappers.h"
#include "font_metrics.h"
#include <hb-ot.h>
#include <hb-subset.h>
#include <sstream>
#include <iomanip>
#include <algorithm>
#include <vector>

hb_face_t* perform_subsetting(
    const FontData& font_data,
    const std::vector<unsigned int>& codepoints,
    const std::vector<AxisConfig>& axis_configs,
    bool strip_hinting,
    bool strip_glyph_names
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
    
    // Collect metrics before subsetting
    FontMetrics metrics_before = collect_font_metrics(face,
        reinterpret_cast<const unsigned char*>(font_data.data.data()),
        font_data.size);

    // Log detailed font information
    log_info("Input font: " + format_file_size(metrics_before.total_size) +
             ", " + std::to_string(metrics_before.glyph_count) + " glyphs");

    // Log all tables with sizes (sorted by size)
    if (!metrics_before.table_sizes.empty()) {
        std::vector<std::pair<std::string, size_t>> sorted_tables;
        for (const auto& [tag, size] : metrics_before.table_sizes) {
            sorted_tables.push_back({tag, size});
        }
        std::sort(sorted_tables.begin(), sorted_tables.end(),
            [](const auto& a, const auto& b) { return a.second > b.second; });

        log_info("Font tables (" + std::to_string(sorted_tables.size()) + " total):");

        // Show significant tables (> 1KB)
        int shown = 0;
        for (const auto& [tag, size] : sorted_tables) {
            if (size > 1024 && shown < 10) {  // Show top 10 tables > 1KB
                log_info("  " + tag + ": " + format_file_size(size));
                shown++;
            }
        }

        // Show summary of smaller tables
        size_t small_table_count = 0;
        size_t small_table_size = 0;
        for (const auto& [tag, size] : sorted_tables) {
            if (size <= 1024) {
                small_table_count++;
                small_table_size += size;
            }
        }
        if (small_table_count > 0) {
            log_debug("  + " + std::to_string(small_table_count) + " smaller tables: " +
                     format_file_size(small_table_size));
        }
    }

    // Log variable font axes if present
    if (!metrics_before.axes.empty()) {
        log_info("Variable font axes found:");
        for (const auto& axis : metrics_before.axes) {
            std::stringstream ss;
            ss << "  " << axis.tag << ": "
               << std::fixed << std::setprecision(0)
               << axis.min_value << ".." << axis.max_value
               << " (default: " << axis.default_value << ")";
            log_info(ss.str());
        }
    }

    // Log hinting tables specifically
    if (metrics_before.total_hinting_size() > 0) {
        std::stringstream ss;
        ss << "Hinting data: " << format_file_size(metrics_before.total_hinting_size());
        if (metrics_before.fpgm_size > 0) ss << " (fpgm: " << format_file_size(metrics_before.fpgm_size) << ")";
        if (metrics_before.prep_size > 0) ss << " (prep: " << format_file_size(metrics_before.prep_size) << ")";
        if (metrics_before.cvt_size > 0) ss << " (cvt: " << format_file_size(metrics_before.cvt_size) << ")";
        log_info(ss.str());
    }
    
    // Create subset input
    HBSubsetInput input(hb_subset_input_create_or_fail());
    if (!input.valid()) {
        log_error("Failed to create subset input");
        return nullptr;
    }

    // Configure subset flags and log what will be removed
    unsigned int flags = HB_SUBSET_FLAGS_DEFAULT;
    std::vector<std::string> optimizations;

    if (strip_hinting && metrics_before.total_hinting_size() > 0) {
        flags |= HB_SUBSET_FLAGS_NO_HINTING;
        flags |= HB_SUBSET_FLAGS_DESUBROUTINIZE; // Required for CFF/CFF2 when dropping hints
        optimizations.push_back("hinting (" + format_file_size(metrics_before.total_hinting_size()) + ")");
    }

    // Note: GLYPH_NAMES flag has inverted logic - setting it KEEPS glyph names
    if (strip_glyph_names && metrics_before.post_size > 0) {
        // By NOT setting the flag, we remove glyph names
        optimizations.push_back("glyph names (" + format_file_size(metrics_before.post_size) + ")");
    } else if (!strip_glyph_names) {
        flags |= HB_SUBSET_FLAGS_GLYPH_NAMES;
    }

    if (!optimizations.empty()) {
        std::string opt_str = "Removing: ";
        for (size_t i = 0; i < optimizations.size(); i++) {
            if (i > 0) opt_str += ", ";
            opt_str += optimizations[i];
        }
        log_info(opt_str);
    }

    hb_subset_input_set_flags(input, (hb_subset_flags_t)flags);

    // Add codepoints to subset
    hb_set_t* unicodes = hb_subset_input_unicode_set(input);
    for (unsigned int codepoint : codepoints) {
        hb_set_add(unicodes, codepoint);
    }
    log_info("Subsetting to " + std::to_string(codepoints.size()) + " codepoints");
    
    // Apply axis configurations
    std::vector<std::string> removed_axes;
    std::vector<std::string> modified_axes;

    if (!axis_configs.empty()) {
        for (const auto& axis : axis_configs) {
            if (axis.tag.length() != 4) {
                log_warn("Invalid axis tag (must be 4 characters): " + axis.tag);
                continue;
            }

            hb_tag_t tag = HB_TAG(axis.tag[0], axis.tag[1], axis.tag[2], axis.tag[3]);

            if (axis.remove) {
                hb_subset_input_pin_axis_to_default(input, face, tag);
                removed_axes.push_back(axis.tag);
            } else {
                hb_subset_input_set_axis_range(input, face, tag,
                                              axis.min_value, axis.max_value, axis.default_value);
                std::stringstream ss;
                ss << axis.tag << ": " << std::fixed << std::setprecision(0)
                   << axis.min_value << ".." << axis.max_value;
                modified_axes.push_back(ss.str());
            }
        }

        if (!removed_axes.empty()) {
            std::string axes_str = "Removing axes: ";
            for (size_t i = 0; i < removed_axes.size(); i++) {
                if (i > 0) axes_str += ", ";
                axes_str += removed_axes[i];
            }
            log_info(axes_str);
        }

        if (!modified_axes.empty()) {
            std::string axes_str = "Modifying axes: ";
            for (size_t i = 0; i < modified_axes.size(); i++) {
                if (i > 0) axes_str += ", ";
                axes_str += modified_axes[i];
            }
            log_info(axes_str);
        }
    }
    
    // Perform the subset operation
    log_debug("Performing subset operation...");
    hb_face_t* subset_face = hb_subset_or_fail(face, input);
    
    if (!subset_face) {
        log_error("Subset operation failed");
        return nullptr;
    }
    
    // Collect metrics after subsetting
    // Get the blob to determine final size
    hb_blob_t* subset_blob = hb_face_reference_blob(subset_face);
    size_t subset_size = subset_blob ? hb_blob_get_length(subset_blob) : 0;

    FontMetrics metrics_after = collect_font_metrics(subset_face, nullptr, subset_size);

    if (subset_blob) {
        hb_blob_destroy(subset_blob);
    }

    // Log detailed results
    log_info("Result: " + format_file_size(metrics_after.total_size) + ", " +
             std::to_string(metrics_after.glyph_count) + " glyphs");
    log_info("Reduction: " + format_file_size(metrics_before.total_size - metrics_after.total_size) +
             " (" + std::to_string((metrics_before.total_size - metrics_after.total_size) * 100 / metrics_before.total_size) + "%)");

    // Show removed tables
    std::vector<std::string> removed_tables;
    size_t removed_size = 0;
    for (const auto& [tag, size] : metrics_before.table_sizes) {
        if (metrics_after.table_sizes.find(tag) == metrics_after.table_sizes.end()) {
            removed_tables.push_back(tag + " (" + format_file_size(size) + ")");
            removed_size += size;
        }
    }

    if (!removed_tables.empty()) {
        std::string msg = "Tables removed (" + format_file_size(removed_size) + "): ";
        for (size_t i = 0; i < removed_tables.size() && i < 5; i++) {
            if (i > 0) msg += ", ";
            msg += removed_tables[i];
        }
        if (removed_tables.size() > 5) {
            msg += " +" + std::to_string(removed_tables.size() - 5) + " more";
        }
        log_info(msg);
    }

    // Show significantly reduced tables
    std::vector<std::pair<std::string, std::pair<size_t, size_t>>> reduced_tables;
    for (const auto& [tag, after_size] : metrics_after.table_sizes) {
        auto before_it = metrics_before.table_sizes.find(tag);
        if (before_it != metrics_before.table_sizes.end()) {
            size_t before_size = before_it->second;
            if (before_size > after_size && (before_size - after_size) > 1024) {
                reduced_tables.push_back({tag, {before_size, after_size}});
            }
        }
    }

    // Sort by reduction amount
    std::sort(reduced_tables.begin(), reduced_tables.end(),
        [](const auto& a, const auto& b) {
            return (a.second.first - a.second.second) > (b.second.first - b.second.second);
        });

    if (!reduced_tables.empty()) {
        log_info("Tables reduced:");
        for (size_t i = 0; i < reduced_tables.size() && i < 5; i++) {
            const auto& [tag, sizes] = reduced_tables[i];
            const auto& [before, after] = sizes;
            int percent = (before - after) * 100 / before;
            log_info("  " + tag + ": " + format_file_size(before) + " → " +
                    format_file_size(after) + " (-" + std::to_string(percent) + "%)");
        }
    }

    // Log remaining axes
    if (!metrics_after.axes.empty() && metrics_after.axes.size() != metrics_before.axes.size()) {
        std::stringstream ss;
        ss << "Remaining axes: ";
        for (size_t i = 0; i < metrics_after.axes.size(); i++) {
            if (i > 0) ss << ", ";
            ss << metrics_after.axes[i].tag;
        }
        log_info(ss.str());
    }

    return subset_face;
}