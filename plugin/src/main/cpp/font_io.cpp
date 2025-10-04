#include "font_io.h"
#include "logging.h"
#include "jni_utils.h"
#include <cstdio>
#include <cerrno>

FontData read_font_file(const std::string& path) {
    FontData result = {{}, 0, false, ""};
    
    log_debug("Reading font file: " + path);
    
    FILE* file = fopen(path.c_str(), "rb");
    if (!file) {
        result.error = "Failed to open file: " + path + " (errno: " + std::to_string(errno) + ")";
        log_error(result.error);
        return result;
    }
    
    // Get file size
    if (fseek(file, 0, SEEK_END) != 0) {
        result.error = "Failed to seek to end of file";
        log_error(result.error);
        fclose(file);
        return result;
    }
    
    long file_size = ftell(file);
    if (file_size < 0) {
        result.error = "Failed to get file size";
        log_error(result.error);
        fclose(file);
        return result;
    }
    
    if (fseek(file, 0, SEEK_SET) != 0) {
        result.error = "Failed to seek to beginning of file";
        log_error(result.error);
        fclose(file);
        return result;
    }
    
    // Read file data
    result.size = static_cast<size_t>(file_size);
    result.data.resize(result.size);
    
    size_t read_size = fread(result.data.data(), 1, result.size, file);
    fclose(file);
    
    if (read_size != result.size) {
        result.error = "Failed to read complete file (read " + std::to_string(read_size) + 
                      " of " + std::to_string(result.size) + " bytes)";
        log_error(result.error);
        return result;
    }
    
    log_debug("Successfully read " + format_file_size(result.size) + " from " + path);
    result.valid = true;
    return result;
}

bool write_font_file(const std::string& path, const char* data, size_t size) {
    log_debug("Writing " + format_file_size(size) + " to " + path);
    
    FILE* file = fopen(path.c_str(), "wb");
    if (!file) {
        log_error("Failed to create output file: " + path + " (errno: " + std::to_string(errno) + ")");
        return false;
    }
    
    size_t written = fwrite(data, 1, size, file);
    fclose(file);
    
    if (written != size) {
        log_error("Failed to write complete file (wrote " + std::to_string(written) + 
                 " of " + std::to_string(size) + " bytes)");
        return false;
    }
    
    log_debug("Successfully wrote font to " + path);
    return true;
}