#ifndef FONTSUBSETTING_HARFBUZZ_WRAPPERS_H
#define FONTSUBSETTING_HARFBUZZ_WRAPPERS_H

#include <hb.h>
#include <hb-subset.h>

// RAII wrapper for hb_blob_t
class HBBlob {
    hb_blob_t* blob;
public:
    explicit HBBlob(hb_blob_t* b) : blob(b) {}
    ~HBBlob() { if (blob) hb_blob_destroy(blob); }
    HBBlob(const HBBlob&) = delete;
    HBBlob& operator=(const HBBlob&) = delete;
    operator hb_blob_t*() { return blob; }
    hb_blob_t* get() { return blob; }
    hb_blob_t* release() { 
        hb_blob_t* temp = blob; 
        blob = nullptr; 
        return temp; 
    }
};

// RAII wrapper for hb_face_t
class HBFace {
    hb_face_t* face;
public:
    explicit HBFace(hb_face_t* f) : face(f) {}
    ~HBFace() { if (face) hb_face_destroy(face); }
    HBFace(const HBFace&) = delete;
    HBFace& operator=(const HBFace&) = delete;
    operator hb_face_t*() { return face; }
    hb_face_t* get() { return face; }
    bool valid() const { return face != nullptr; }
};

// RAII wrapper for hb_subset_input_t
class HBSubsetInput {
    hb_subset_input_t* input;
public:
    explicit HBSubsetInput(hb_subset_input_t* i) : input(i) {}
    ~HBSubsetInput() { if (input) hb_subset_input_destroy(input); }
    HBSubsetInput(const HBSubsetInput&) = delete;
    HBSubsetInput& operator=(const HBSubsetInput&) = delete;
    operator hb_subset_input_t*() { return input; }
    hb_subset_input_t* get() { return input; }
    bool valid() const { return input != nullptr; }
};

#endif // FONTSUBSETTING_HARFBUZZ_WRAPPERS_H