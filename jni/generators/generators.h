#ifndef GENERATORS_H
#define GENERATORS_H

#include <stdlib.h>
#include <math.h>
#include "effects.h"

#define TABLE_SIZE 2048
#define BUFF_SIZE 512 // 512 samples, each with one short for each channel
#define SAMPLE_RATE 44100
#define bool _Bool
#define false 0
#define true 1

typedef struct Generator_t {
	void *config;
	void (*set)(void *, float, float);
	void (*generate)(void *, float **, int);
	void (*destroy)(void *);
} Generator;

static inline void initGenerator(Generator *generator, void *config,
		void (*set), void (*generate), void (*destroy)) {
	generator->config = config;
	generator->set = set;
	generator->generate = generate;
	generator->destroy = destroy;
}

#endif // GENERATORS_H
