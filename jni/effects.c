#include "effects.h"

static inline void underguard(float *x) {
  	union {
	    u_int32_t i;
	    float f;
  	} ix;
  	ix.f = *x;
  	if((ix.i & 0x7f800000)==0) *x=0.0f;
}

static void inject_set(ReverbState *r,int inject) {
  	int i;
  	for(i=0;i<numcombs;i++){
	    int off=(1000-inject)*r->comb[i].size/scaleroom;
	    r->comb[i].extpending=r->comb[i].injptr-off;
	    if(r->comb[i].extpending<r->comb[i].buffer)r->comb[i].extpending+=r->comb[i].size;
  	}
}

static ReverbState *initReverbState() {
  	int inject = 300;
  	const int *combtuning = combL;
  	const int *alltuning = allL; 
  	int i;
  	ReverbState *r = calloc(1, sizeof(ReverbState));
  
  	r->comb[0].buffer=r->bufcomb0;
  	r->comb[1].buffer=r->bufcomb1;
  	r->comb[2].buffer=r->bufcomb2;
  	r->comb[3].buffer=r->bufcomb3;
  	r->comb[4].buffer=r->bufcomb4;
  	r->comb[5].buffer=r->bufcomb5;
  	r->comb[6].buffer=r->bufcomb6;
  	r->comb[7].buffer=r->bufcomb7;

  	for(i=0;i<numcombs;i++)
	    r->comb[i].size=combtuning[i];
  	for(i=0;i<numcombs;i++)
	    r->comb[i].injptr=r->comb[i].buffer;

  	r->allpass[0].buffer=r->bufallpass0;
  	r->allpass[1].buffer=r->bufallpass1;
  	r->allpass[2].buffer=r->bufallpass2;
  	r->allpass[3].buffer=r->bufallpass3;
  	for(i=0;i<numallpasses;i++)
	    r->allpass[i].size=alltuning[i];
  	for(i=0;i<numallpasses;i++)
	    r->allpass[i].bufptr=r->allpass[i].buffer;
	    
	inject_set(r,inject);
  	for(i=0;i<numcombs;i++)
	    r->comb[i].extptr=r->comb[i].extpending;
	    
  return r;
}

static inline float allpass_process(allpass_state *a, float  input){
  	float val    = *a->bufptr;
  	float output = val - input;
  
  	*a->bufptr   = val * .5f + input;
  	underguard(a->bufptr);
  
  	if(a->bufptr<=a->buffer) a->bufptr += a->size;
  	--a->bufptr;

  	return output;
}

static inline float comb_process(comb_state *c, float  feedback, float  hfDamp, float  input){
  	float val      = *c->extptr;
  	c->filterstore = val + (c->filterstore - val)*hfDamp;
  	underguard(&c->filterstore);

  	*c->injptr     = input + c->filterstore * feedback;
  	underguard(c->injptr);

  	if(c->injptr<=c->buffer) c->injptr += c->size;
  	--c->injptr;
  	if(c->extptr<=c->buffer) c->extptr += c->size;
  	--c->extptr;
  	
  return val;
}

void initEffect(Effect *effect, bool on, bool dynamic, void *config,
				void (*set), void (*process), void (*destroy)) {
	effect->on = on;
	effect->dynamic = dynamic;
	effect->config = config;
	effect->set = set;
	effect->process = process;
	effect->destroy = destroy;				
}

void initAdsrPoints(AdsrConfig *config) {
	config->adsrPoints[0].sampleCents = 0;
	config->adsrPoints[1].sampleCents = 0.25f;
	config->adsrPoints[2].sampleCents = 0.5f;
	config->adsrPoints[3].sampleCents = 0.75f;
	config->adsrPoints[4].sampleCents = 1;
}

AdsrConfig *adsrconfig_create(int totalSamples) {
	AdsrConfig *config = (AdsrConfig *)malloc(sizeof(AdsrConfig));
	initAdsrPoints(config);
	config->active = false;
	config->initial = config->end = 0.0001f;
	config->sustain = 0.6f;
	config->peak = 1.0f;
	resetAdsr(config);
	updateAdsr(config, totalSamples);
	return config;
}

void adsr_process(void *p, float **buffers, int size) {
	AdsrConfig *config = (AdsrConfig *)p;
	if (!config->active) return;
	int i;
	for (i = 0; i < size; i++) {
		if (++config->currSampleNum < config->gateSample) {
			if (config->rising) { // attack phase
				config->currLevel += config->attackCoeff*(config->peak/0.63f - config->currLevel);
				if (config->currLevel > 1.0f) {
					config->currLevel = 1.0f;
					config->rising = false;
				}
			} else { // decal/sustain
				config->currLevel += config->decayCoeff * (config->sustain - config->currLevel)/0.63f;
			}
		} else if (config->currSampleNum < config->adsrPoints[4].sampleNum) { // past gate sample, go to release phase
			config->currLevel += config->releaseCoeff * (config->end - config->currLevel)/0.63f;
			if (config->currLevel < config->end) {
				config->currLevel = config->end;
			}
		} else if (config->currSampleNum < config->totalSamples) {
			config->currLevel = 0;
		} else {
			resetAdsr(config);
		}
		buffers[0][i] *= config->currLevel;
		buffers[1][i] *= config->currLevel;
    }
}

void adsrconfig_destroy(void *p) {
	AdsrConfig *config = (AdsrConfig *)p;
	free(config->adsrPoints);
	free(config);
}

void updateAdsr(AdsrConfig *config, int totalSamples) {
	config->totalSamples = totalSamples;
	int i, length;
	for (i = 0; i < 5; i++) {
		config->adsrPoints[i].sampleNum = (int)(config->adsrPoints[i].sampleCents*totalSamples);
	}
	for (i = 0; i < 4; i++) {
		length = config->adsrPoints[i + 1].sampleNum - config->adsrPoints[i].sampleNum; 
		if (i == 0)
			config->attackCoeff = (1.0f - config->initial)/(length + 1);
		else if (i == 1)
			config->decayCoeff = 1.0f/(length + 1);
		else if (i == 3)
			config->releaseCoeff = (1.0f - config->end)/(length + 1);
	}
	config->gateSample = config->adsrPoints[3].sampleNum;
}

void resetAdsr(AdsrConfig *config) {
	config->currSampleNum  = 0;
	config->currLevel = config->initial;
	config->rising = true;
}

DecimateConfig *decimateconfig_create(float bits, float rate) {
    DecimateConfig *decimateConfig = (DecimateConfig *)malloc(sizeof(DecimateConfig));
	decimateConfig->cnt = 0;
	decimateConfig->y = 0;
	decimateConfig->bits = (int)bits;
	decimateConfig->rate = rate;
	return decimateConfig;
}

void decimateconfig_set(void *p, float bits, float rate) {
	DecimateConfig *config = (DecimateConfig *)p;
	if ((int)bits != 0)
		config->bits = (int)bits;
	config->rate = rate;	
}

void decimate_process(void *p, float **buffers, int size) {
	DecimateConfig *config = (DecimateConfig *)p;
    int m = 1 << (config->bits - 1);
	int i;
	for (i = 0; i < size; i++) {
	    config->cnt += config->rate;
	    if (config->cnt >= 1) {
	        config->cnt -= 1;
		config->y = (long int)(buffers[0][i]*m)/(float)m;
	    }
	    buffers[0][i] = buffers[1][i] = config->y;
	}
}

void decimateconfig_destroy(void *p) {	
	if(p != NULL) free((DecimateConfig *)p);
}

DelayConfigI *delayconfigi_create(float delay, float feedback) {
	// allocate memory and set feedback parameter
	DelayConfigI *p = (DelayConfigI *)malloc(sizeof(DelayConfigI));
	pthread_mutex_init(&p->mutex, NULL);
	p->delayBuffer = (float **)malloc(2*sizeof(float *));
	p->delayBuffer[0] = (float *)malloc(SAMPLE_RATE*sizeof(float));
	p->delayBuffer[1] = (float *)malloc(SAMPLE_RATE*sizeof(float));
	p->delayBufferSize = SAMPLE_RATE;
	p->rp[0] = p->wp[1] = 0;
	delayconfigi_set(p, delay, feedback);
	p->wet = 0.5f;
	p->numBeats = 4;
	p->beatmatch = false;
	return p;
}

void delayconfigi_set(void *p, float delay, float feedback) {
	DelayConfigI *config = (DelayConfigI *)p;
	delayconfigi_setDelayTime(config, delay);
	delayconfigi_setFeedback(config, feedback);
}

void delayconfigi_setDelayTime(DelayConfigI *config, float delay) {
	config->delayTime = delay > 0 ? (delay <= 1 ? delay : 1) : 0;
	if (config->delayTime < 0.0001) config->delayTime = 0.0001;
	int i, *rp, *wp;
	pthread_mutex_lock(&config->mutex);
	config->delaySamples = config->delayTime*SAMPLE_RATE;
	for (i = 0; i < 2; i++) {
		rp = &(config->rp[i]);
		wp = &(config->wp[i]);
		float rpf = *wp - config->delaySamples; // read chases write
		while (rpf < 0)
			rpf += config->delayBufferSize;
		*rp = floorf(rpf);
		if (*rp >= config->delayBufferSize) (*rp) = 0;
		config->alpha[i] = rpf - (*rp);
		config->omAlpha[i] = 1.0f - config->alpha[i];
	}
	pthread_mutex_unlock(&config->mutex);
}

void delayconfigi_setFeedback(DelayConfigI *config, float feedback) {
	int i;
	for (i = 0; i < 2; i++)
		config->feedback[i] = feedback > 0.f ? (feedback < 1.f ? feedback : 0.9999999f) : 0.f;
}

void delayconfigi_setNumBeats(DelayConfigI *config, int numBeats) {
	if (numBeats == config->numBeats) return;
	config->numBeats = numBeats;
	delayconfigi_syncToBPM(config);
}

void delayconfigi_syncToBPM(DelayConfigI *config) {
	if (!config->beatmatch) return;
	// divide by 60 for seconds, divide by 16 for 16th notes
	float newTime = (BPM/960.0f)*(float)config->numBeats;
	delayconfigi_setDelayTime(config, newTime);	
}

float interp(DelayConfigI *config, int channel, int position) {
	float out = config->delayBuffer[channel][position] * config->omAlpha[channel];
	if (position + 1 < config->delayBufferSize)
		out += config->delayBuffer[channel][position + 1] * config->alpha[channel];
	else
		out += config->delayBuffer[channel][0] * config->alpha[channel];
	return out;
}

void delayi_process(void *p, float **buffers, int size) {
	DelayConfigI *config = (DelayConfigI *)p;
	int channel, samp;
	for (channel = 0; channel < 2; channel++) {
		for (samp = 0; samp < size; samp++) {
			buffers[channel][samp] = delayi_tick(config, buffers[channel][samp], channel);
		}
	}
}

float delayi_tick(DelayConfigI *config, float in, int channel) {
	pthread_mutex_lock(&config->mutex);
	if ((config->rp[channel]) >= config->delayBufferSize) ((config->rp[channel])) = 0;
	if ((config->wp[channel]) >= config->delayBufferSize) ((config->wp[channel])) = 0;
	float interpolated = interp(config, channel, (config->rp[channel])++);
	pthread_mutex_unlock(&config->mutex);
	int wpi = floorf((config->wp[channel])++);
	config->out = interpolated*config->wet + in*(1 - config->wet);
	if (config->out > 1) config->out = 1;
	config->delayBuffer[channel][wpi] = in + config->out*config->feedback[channel];
	return config->out;	
}

void delayconfigi_destroy(void *p){
	DelayConfigI *config = (DelayConfigI *)p;
	free(config->delayBuffer);
	free((DelayConfigI *)p);
}

FilterConfig *filterconfig_create(float f, float r) {
	FilterConfig *config = (FilterConfig *)malloc(sizeof(FilterConfig));
	config->hp = false;
	config->in1[0] = config->in1[1] = 0;
	config->in2[0] = config->in2[1] = 0;
	config->out1[0] = config->out1[1] = 0;
	config->out2[0] = config->out2[1] = 0;
	filterconfig_set(config, f, r);
	return config;
}

void filterconfig_set(void *p, float f, float r) {
	FilterConfig *config = (FilterConfig *)p;
	config->f = f;
	config->r = r;	
	float f0 = f * INV_SAMPLE_RATE;
	if (config->hp) { // highpass filter settings
		config->c = f0 < 0.1f ? f0 * M_PI : tan(M_PI * f0);
		config->a1 = 1.0f/(1.0f + config->r * config->c + config->c * config->c);
		config->a2 = -2.0f * config->a1;
		config->a3 = config->a1;
		config->b1 = 2.0f * (config->c * config->c - 1.0f) * config->a1;
	} else { // lowpass filter settings
		// for frequencies < ~ 4000 Hz, approximate the tan function as an optimization.
		config->c = f0 < 0.1f ? 1.0f / (f0 * M_PI) : tan((0.5f - f0) * M_PI);
		config->a1 = 1.0f/(1.0f + config->r * config->c + config->c * config->c);
		config->a2 = 2.0f * config->a1;
		config->a3 = config->a1;
		config->b1 = 2.0f * (1.0f - config->c * config->c) * config->a1;
	}
	config->b2 = (1.0f - config->r * config->c + config->c * config->c) * config->a1;
}

void filter_process(void *p, float **buffers, int size) {
	FilterConfig *config = (FilterConfig *)p;
	int channel, samp;
	for (channel = 0; channel < 2; channel++) {
		for(samp = 0; samp < size; samp++) {
			float out = config->a1 * buffers[channel][samp] +
				        config->a2 * config->in1[channel] +
					    config->a3 * config->in2[channel] -
					    config->b1 * config->out1[channel] -
					    config->b2 * config->out2[channel];
			config->in2[channel] = config->in1[channel];
			config->in1[channel] = buffers[channel][samp];
			config->out2[channel] = config->out1[channel];
			config->out1[channel] = out;
			buffers[channel][samp] = out;
		}	
	}
}

void filterconfig_destroy(void *p) {
	free((FilterConfig *)p);
}

FlangerConfig *flangerconfig_create(float delayTime, float feedback) {
	FlangerConfig *flangerConfig = (FlangerConfig *)malloc(sizeof(FlangerConfig));
	flangerConfig->delayConfig = delayconfigi_create(delayTime, feedback);
	flangerconfig_set(flangerConfig, delayTime, feedback);
	flangerConfig->mod = sinewave_create();
	flangerConfig->modAmt = .5f;
	return flangerConfig;
}

void flangerconfig_set(void *p, float delayTime, float feedback) {
	FlangerConfig *config = (FlangerConfig *)p;
	flangerconfig_setBaseTime(config, delayTime);
	flangerconfig_setTime(config, delayTime);
	flangerconfig_setFeedback(config, feedback);
}

void flangerconfig_setBaseTime(FlangerConfig *config, float baseTime) {
	config->baseTime = baseTime;
}

void flangerconfig_setTime(FlangerConfig *config, float time) {
	float scaledTime = time * (MAX_FLANGER_DELAY - MIN_FLANGER_DELAY) + MIN_FLANGER_DELAY;
	delayconfigi_setDelayTime(config->delayConfig, scaledTime);
}

void flangerconfig_setFeedback(FlangerConfig *config, float feedback) {
	delayconfigi_setFeedback(config->delayConfig, feedback);
}

void flangerconfig_setModRate(FlangerConfig *config, float modRate) {
	sinewave_setRateInSamples(config->mod, modRate*SAMPLE_RATE/100);
}

void flangerconfig_setModAmt(FlangerConfig *config, float modAmt) {
	config->modAmt = modAmt;
}

void flanger_process(void *p, float **buffers, int size) {
	FlangerConfig *config = (FlangerConfig *)p;
	int channel, samp;
	for (channel = 0; channel < 2; channel++) {
		for (samp = 0; samp < size; samp++) {
			if (channel == 0)
				//flangerconfig_setTime(config, config->baseTime * (1.0f + config->modAmt * sinewave_tick(config->mod)));
				flangerconfig_setTime(config, config->baseTime * (1.0f + config->modAmt * sin(config->count++ * INV_SAMPLE_RATE * config->mod->rate/20)));
			buffers[channel][samp] = delayi_tick(config->delayConfig, buffers[channel][samp], channel);
		}	
	}	
}

void flangerconfig_destroy(void *p) {
	FlangerConfig *config = (FlangerConfig *)p;
	delayconfigi_destroy(config->delayConfig);
	free(config);
}

PitchConfig *pitchconfig_create(float shift) {
	return NULL;
}

void pitchconfig_set(float shift) {

}

void pitch_process(void *config, float **buffers, int size) {

}

void pitchconfig_destroy(void *config) {

}

VolumePanConfig *volumepanconfig_create(float volume, float pan) {
    VolumePanConfig *p = (VolumePanConfig *)malloc(sizeof(VolumePanConfig));
	p->volume = volume;
	p->pan = pan;
	return p;
}

void volumepanconfig_set(void *p, float volume, float pan) {
	VolumePanConfig *config = (VolumePanConfig *)p;
	config->volume = volume;
	config->pan = pan;
}

void volumepan_process(void *p, float **buffers, int size) {
	VolumePanConfig *config = (VolumePanConfig *)p;
	float leftVolume = (1 - config->pan)*config->volume;
	float rightVolume = config->pan*config->volume;
	int i;
	for (i = 0; i < size; i++) {
		if (buffers[0][i] == 0) continue;
		buffers[0][i] *= leftVolume; // left channel
	}
	for (i = 0; i < size; i++) {
		if (buffers[1][i] == 0) continue;
		buffers[1][i] *= rightVolume; // right channel	
	}
}

void volumepanconfig_destroy(void *p) {
	if(p != NULL) free((VolumePanConfig *)p);
}

ReverbConfig *reverbconfig_create(float feedback, float hfDamp) {
	ReverbConfig *config = (ReverbConfig *)malloc(sizeof(ReverbConfig));
	config->state = initReverbState();
	config->feedback = feedback;
	config->hfDamp = hfDamp;
	
	return config;
}

void reverbconfig_set(void *p, float feedback, float hfDamp) {
	ReverbConfig *config = (ReverbConfig *)p;
	config->feedback = feedback;
	config->hfDamp = hfDamp;
}

void reverb_process(void *p, float **buffers, int size) {	
	ReverbConfig *config = (ReverbConfig *)p;
  	float out, val=0;
  	int i, j;

  	for (i = 0; i < size; i++) {
    	out = 0;
    	val = buffers[0][i];
	    for(j = 0; j < numcombs; j++)
      		out += comb_process(config->state->comb + j, config->feedback, config->hfDamp, val);
	    for(j = 0; j < numallpasses; j++)
    	    out = allpass_process(config->state->allpass + j, out);
	    buffers[0][i] = buffers[1][i] = out;
	}
}

void reverbconfig_destroy(void *p) {
	ReverbConfig *config = (ReverbConfig *)p;
	free(config->state);
	config->state = NULL;
	free(config);
	config = NULL;
}

void swap(float *a , float *b) {
    float tmp;
    tmp = *a;
    (*a) = (*b);
    (*b) = tmp;
}

void reverse(float buffer[], int begin, int end) {
	int i, j;
    //swap 1st with last, then 2nd with last-1, etc.  Till we reach the middle of the string.
	for (i = begin, j = end - 1; i < j; i++, j--) {
        swap( &buffer[i] , &buffer[j]);	
	}
}

void normalize(float buffer[], int size) {
	float maxSample = 0;	
	int i;
	for (i = 0; i < size; i++) {
		if (abs(buffer[i]) > maxSample) {
			maxSample = abs(buffer[i]);
		}
	}
	if (maxSample != 0) {
		for (i = 0; i < size; i++) {
			buffer[i] /= maxSample;
		}
	}		
}
