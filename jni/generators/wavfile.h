#ifndef WAVFILE_H
#define WAVFILE_H

#include "generators.h"

#define CONVMYFLT (1./32768.)

typedef struct WavFile_t {
	float **buffers; // buffer to hold wav data
	int totalSamples;
	int currSample;
	int loopBegin;
	int loopEnd;
	bool looping;
} WavFile;

WavFile *wavfile_create();
void wavfile_reset(WavFile *config);

static inline void wavfile_generate(WavFile *config, float **inBuffer, int size) {
	// start with all zeros
	memset(inBuffer, 0, (BUFF_SIZE) * 2 * sizeof(float));
	if (config->currSample < config->loopEnd) {
		int totalSize = 0;
		int nextSize; // how many samples to copy from the source
		while (totalSize < BUFF_SIZE) {
			if (config->currSample + BUFF_SIZE - totalSize
					>= config->loopEnd) {
				// at the end of the window - copy all samples that are left
				nextSize = config->loopEnd - config->currSample;
			} else {
				nextSize = BUFF_SIZE - totalSize; // plenty of samples left to copy :)
			}
			// copy the next block of data from the scratch buffer into the current float buffer for streaming
			memcpy(&(inBuffer[0][totalSize]),
					&(config->buffers[0][config->currSample]),
					nextSize * sizeof(float));
			memcpy(&(inBuffer[1][totalSize]),
					&(config->buffers[1][config->currSample]),
					nextSize * sizeof(float));

			totalSize += nextSize;
			// increment sample counter to reflect bytes written so far
			config->currSample += nextSize;
			if (config->currSample >= config->loopEnd) {
				if (config->looping) {
					// if we are looping, and we're past the end, loop back to the beginning
					config->currSample = config->loopBegin;
				} else {
					break; // not looping, so we can play less than BUFF_SIZE samples
				}
			}
		}
	}
}

void wavfile_destroy(void *config);

#endif // WAVFILE_H