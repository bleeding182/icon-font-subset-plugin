#ifndef FONTSUBSETTING_FONT_IO_H
#define FONTSUBSETTING_FONT_IO_H

#include <string>
#include <vector>

struct FontData {
    std::vector<char> data;
    size_t size;
    bool valid;
    std::string error;
};

// Read a font file from disk
FontData read_font_file(const std::string& path);

// Write font data to disk
bool write_font_file(const std::string& path, const char* data, size_t size);

// Format file size for display
std::string format_file_size(size_t size);

#endif // FONTSUBSETTING_FONT_IO_H