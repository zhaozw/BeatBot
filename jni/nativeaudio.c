#include "nativeaudio.h"

// __android_log_print(ANDROID_LOG_INFO, "YourApp", "formatted message");

static int trackCount = 0;

// engine interfaces
static SLObjectItf engineObject = NULL;
static SLEngineItf engineEngine = NULL;
static AAssetManager* assetManager = NULL;

// output mix interfaces
static SLObjectItf outputMixObject = NULL;

//static float zeroBuffer[2][BUFF_SIZE];

void updateNextNoteSamples() {
	int trackNum;
	for (trackNum = 0; trackNum < NUM_TRACKS; trackNum++) {
		Track *track = getTrack(NULL, NULL, trackNum);
		if (track->nextEventNode != NULL) {
			track->nextStartSample = tickToSample(
					track->nextEventNode->event->onTick);
			track->nextStopSample = tickToSample(
					track->nextEventNode->event->offTick);
		}
	}
}

// create the engine and output mix objects
void Java_com_kh_beatbot_BeatBotActivity_createEngine(JNIEnv *env, jclass clazz,
		jobject _assetManager) {
	SLresult result;
	(void *) clazz; // avoid warnings about unused paramaters
	initTicker();

	// create engine
	result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);

	// realize the engine
	result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);

	// get the engine interface, which is needed in order to create other objects
	result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE,
			&engineEngine);

	// create output mix, with volume specified as a non-required interface
	const SLInterfaceID ids[1] = { SL_IID_VOLUME };
	const SLboolean req[1] = { SL_BOOLEAN_FALSE };
	result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1,
			ids, req);

	// realize the output mix
	result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);

	// use asset manager to open asset by filename
	assetManager = AAssetManager_fromJava(env, _assetManager);
}

MidiEvent* initEvent(long onTick, long offTick, float volume, float pan,
		float pitch) {
	MidiEvent *event = (MidiEvent *) malloc(sizeof(MidiEvent));
	event->muted = false;
	event->onTick = onTick;
	event->offTick = offTick;
	event->volume = volume;
	event->pan = pan;
	event->pitch = pitch;
	return event;
}

void initTrack(Track *track, AAsset *asset) {
	// asset->getLength() returns size in bytes.  need size in shorts, minus 22 shorts of .wav header
	track->num = trackCount;
	track->effectHead = NULL;
	track->nextEventNode = NULL;
	track->nextStartSample = track->nextStopSample = -1;
	track->currBufferFloat = (float **) malloc(2 * sizeof(float *));
	track->currBufferFloat[0] = (float *) calloc(BUFF_SIZE, sizeof(float));
	track->currBufferFloat[1] = (float *) calloc(BUFF_SIZE, sizeof(float));
	track->armed = false;
	track->playing = track->previewing = false;
	track->mute = track->solo = false;
	track->shouldSound = true;
	track->primaryVolume = track->noteVolume = .8f;
	track->primaryPan = track->primaryPitch = track->notePan =
			track->notePitch = .5f;
	track->generator = malloc(sizeof(Generator));
	initGenerator(track->generator, wavfile_create(asset), wavfile_reset,
			wavfile_generate, wavfile_destroy);
	track->volPan = initEffect(-1, true, volumepanconfig_create(),
			volumepanconfig_set, volumepan_process, volumepanconfig_destroy);
	track->pitch = initEffect(-1, false, pitchconfig_create(),
			pitchconfig_setShift, pitch_process, pitchconfig_destroy);
	track->adsr = initEffect(-1, true,
			adsrconfig_create(
					((WavFile *) (track->generator->config))->totalSamples),
			NULL, adsr_process, adsrconfig_destroy);
}

static inline void interleaveFloatsToShorts(float left[], float right[],
		short interleaved[], int size) {
	int i;
	for (i = 0; i < size; i++) {
		interleaved[i * 2] = left[i] * CONV16BIT;
		interleaved[i * 2 + 1] = right[i] * CONV16BIT;
	}
}

static inline void processEffects(Track *track) {
	track->volPan->process(track->volPan->config,
						track->currBufferFloat, BUFF_SIZE);
	EffectNode *effectNode = track->effectHead;
	while (effectNode != NULL) {
		if (effectNode->effect != NULL && effectNode->effect->on) {
			effectNode->effect->process(effectNode->effect->config,
					track->currBufferFloat, BUFF_SIZE);
		}
		effectNode = effectNode->next;
	}
}

static inline void processEffectsForAllTracks() {
	int trackNum;
	for (trackNum = 0; trackNum < NUM_TRACKS; trackNum++) {
		processEffects(&tracks[trackNum]);
	}
}

static inline void mixTracks() {
	int channel, samp, trackNum;
	float total;
	for (channel = 0; channel < 2; channel++) {
		for (samp = 0; samp < BUFF_SIZE; samp++) {
			total = 0;
			for (trackNum = 0; trackNum < NUM_TRACKS; trackNum++) {
				if ((&tracks[trackNum])->shouldSound)
					total += (&tracks[trackNum])->currBufferFloat[channel][samp]
							* .7f;
			}
			openSlOut->currBufferFloat[channel][samp] =
					total > -1 ? (total < 1 ? total : 1) : -1;
		}
	}
	// combine the two channels of floats into one buffer of shorts,
	// interleaving L and R samples
	interleaveFloatsToShorts(openSlOut->currBufferFloat[0],
			openSlOut->currBufferFloat[1], openSlOut->currBufferShort,
			BUFF_SIZE);
}

void updateNextEvent(Track *track) {
	if (track->eventHead == NULL) {
		track->nextEventNode = NULL;
		track->nextStartSample = -1;
		track->nextStopSample = -1;
		return; // no midi notes in this track
	}
	// find event right after current tick
	MidiEventNode *ptr = track->eventHead;
	long currTick = sampleToTick(currSample);
	while (ptr->next != NULL && ptr->event->offTick <= currTick)
		ptr = ptr->next;
	if (ptr->event->offTick <= currTick || ptr->event->onTick >= loopEndTick) {
		// no events after curr tick, or next event after loop end.
		// return fist event after loop begin
		ptr = track->eventHead;
		while (ptr->next != NULL && ptr->event->onTick < loopBeginTick)
			ptr = ptr->next;
	}
	track->nextEventNode = ptr;
	track->nextStartSample = tickToSample(track->nextEventNode->event->onTick);
	track->nextStopSample = tickToSample(track->nextEventNode->event->offTick);
}

void soundTrack(Track *track) {
	track->volPan->set(track->volPan->config,
			track->primaryVolume * track->noteVolume,
			track->primaryPan * track->notePan);
	//pitchconfig_setShift((PitchConfig *)track->effects[DYNAMIC_PITCH_ID].config, pitch*2 - 1);
	AdsrConfig *adsrConfig = (AdsrConfig *) track->adsr->config;
	adsrConfig->active = true;
	resetAdsr(adsrConfig);
	//if (track->outputPlayerPitch != NULL) {
	//	(*(track->outputPlayerPitch))->SetRate(track->outputPlayerPitch, (short)((pitch + track->primaryPitch)*750 + 500));
	//}
}

void stopSoundingTrack(Track *track) {
	wavfile_reset((WavFile *) track->generator->config);
	((AdsrConfig *) track->adsr->config)->active = false;
}

void playTrack(Track *track) {
	MidiEventNode *nextEventNode = track->nextEventNode;
	track->noteVolume = nextEventNode->event->volume;
	track->notePan = nextEventNode->event->pan;
	track->notePitch = nextEventNode->event->pitch;
	track->playing = true;
	soundTrack(track);
}

void stopTrack(Track *track) {
	// update next track
	updateNextEvent(track);
	if (!track->playing)
		return;
	track->playing = false;
	stopSoundingTrack(track);
}

void stopAllTracks() {
	int trackNum;
	for (trackNum = 0; trackNum < trackCount; trackNum++) {
		stopTrack(&tracks[trackNum]);
	}
}

void previewTrack(int trackNum) {
	Track *track = getTrack(NULL, NULL, trackNum);
	track->previewing = true;
	if (!track->playing) {
		soundTrack(track);
	}
}

void stopPreviewingTrack(int trackNum) {
	Track *track = getTrack(NULL, NULL, trackNum);
	track->previewing = false;
	if (!track->playing)
		stopSoundingTrack(track);
}

static inline void generateNextBuffer() {
	int trackNum, samp, channel;
	for (samp = 0; samp < BUFF_SIZE; samp++) {
		if (currSample > loopEndSample) {
			stopAllTracks();
			currSample = loopBeginSample;
		}
		for (trackNum = 0; trackNum < NUM_TRACKS; trackNum++) {
			Track *track = &tracks[trackNum];
			if (!track->armed)
				continue;
			if (currSample == track->nextStartSample) {
				playTrack(track);
			} else if (currSample == track->nextStopSample) {
				stopTrack(track);
			}
			if (track->playing || track->previewing) {
				wavfile_tick((WavFile *) track->generator->config,
						track->tempSample);
			} else {
				track->tempSample[0] = track->tempSample[1] = 0;
			}
			for (channel = 0; channel < 2; channel++) {
				track->currBufferFloat[channel][samp] =
						track->tempSample[channel];
			}
		}
		if (openSlOut->armed)
			currSample++;
	}
}

// this callback handler is called every time a buffer finishes playing
void bufferQueueCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
	// calculate the next buffer
	generateNextBuffer();
	processEffectsForAllTracks();
	mixTracks();
	// enqueue the buffer
	if (openSlOut->anyTrackArmed)
		(*bq)->Enqueue(bq, openSlOut->currBufferShort,
				BUFF_SIZE * 2 * sizeof(short));
}

MidiEvent *findEvent(Track *track, long tick) {
	MidiEventNode *cur_ptr = track->eventHead;
	while (cur_ptr != NULL) {
		if (cur_ptr->event->onTick == tick || cur_ptr->event->offTick == tick)
			return cur_ptr->event;
		cur_ptr = cur_ptr->next;
	}
	return NULL;
}

// add a midi event to the track's event linked list, inserting in order of onTick
MidiEventNode *addEvent(Track *track, MidiEvent *event) {
	MidiEventNode *temp = (MidiEventNode *) malloc(sizeof(MidiEventNode));
	temp->event = event;
	MidiEventNode *cur_ptr = track->eventHead;
	while (cur_ptr != NULL) {
		if (temp->event->onTick > cur_ptr->event->onTick
				&& (cur_ptr->next == NULL
						|| temp->event->onTick < cur_ptr->next->event->onTick)) {
			temp->next = cur_ptr->next;
			cur_ptr->next = temp;
			return temp;
		}
		cur_ptr = cur_ptr->next;
	}
	// if we get here, the new event is before any other event, or is the first event
	temp->next = track->eventHead;
	track->eventHead = temp;
	return temp;
}

// Deleting a node from List depending upon the data in the node.
MidiEventNode *removeEvent(Track *track, long onTick, bool muted) {
	MidiEventNode *prev_ptr = NULL, *cur_ptr = track->eventHead;
	while (cur_ptr != NULL) {
		if ((muted && cur_ptr->event->muted)
				|| cur_ptr->event->onTick == onTick) {
			if (cur_ptr == track->eventHead) {
				track->eventHead = cur_ptr->next;
				free(cur_ptr->event);
				free(cur_ptr);
				cur_ptr = NULL;
				return track->eventHead;
			} else {
				prev_ptr->next = cur_ptr->next;
				free(cur_ptr->event);
				free(cur_ptr);
				cur_ptr = NULL;
				return track->eventHead;
			}
		} else {
			prev_ptr = cur_ptr;
			cur_ptr = cur_ptr->next;
		}
	}
	return track->eventHead;
}

void printLinkedList(MidiEventNode *head) {
	__android_log_print(ANDROID_LOG_DEBUG, "LL", "Elements:");
	MidiEventNode *cur_ptr = head;
	while (cur_ptr != NULL) {
		__android_log_print(ANDROID_LOG_DEBUG, "LL Element", "onTick = %ld",
				cur_ptr->event->onTick);
		__android_log_print(ANDROID_LOG_DEBUG, "LL Element", "offTick = %ld",
				cur_ptr->event->offTick);
		cur_ptr = cur_ptr->next;
	}
}

jboolean Java_com_kh_beatbot_BeatBotActivity_initTrack(JNIEnv *env,
		jclass clazz, jstring filename) {
	if (trackCount >= NUM_TRACKS) {
		return JNI_FALSE;
	}
	// convert Java string to UTF-8
	const char *utf8 = (*env)->GetStringUTFChars(env, filename, NULL);

	AAsset* asset = AAssetManager_open(assetManager, utf8, AASSET_MODE_UNKNOWN);

	// release the Java string and UTF-8
	(*env)->ReleaseStringUTFChars(env, filename, utf8);

	// the asset might not be found
	if (asset == NULL) {
		return JNI_FALSE;
	}

	Track *track = getTrack(env, clazz, trackCount);
	initTrack(track, asset);
	// all done! increment track count
	trackCount++;
	return JNI_TRUE;
}

// create asset audio player
jboolean Java_com_kh_beatbot_BeatBotActivity_createAssetAudioPlayer(JNIEnv *env,
		jclass clazz) {

	openSlOut = malloc(sizeof(OpenSlOut));
	openSlOut->currBufferFloat = (float **) malloc(2 * sizeof(float *));
	openSlOut->currBufferFloat[0] = (float *) calloc(BUFF_SIZE, sizeof(float));
	openSlOut->currBufferFloat[1] = (float *) calloc(BUFF_SIZE, sizeof(float));
	openSlOut->armed = openSlOut->anyTrackArmed = false;

	// configure audio sink
	SLDataLocator_OutputMix loc_outmix = { SL_DATALOCATOR_OUTPUTMIX,
			outputMixObject };
	SLDataSink audioSnk = { &loc_outmix, NULL };

	// config audio source for output buffer (source is a SimpleBufferQueue)
	SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {
			SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2 };
	SLDataSource outputAudioSrc = { &loc_bufq, &format_pcm };

	// create audio player for output buffer queue
	const SLInterfaceID ids1[] = { SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
			SL_IID_PLAYBACKRATE, SL_IID_MUTESOLO };
	const SLboolean req1[] = { SL_BOOLEAN_TRUE };
	(*engineEngine)->CreateAudioPlayer(engineEngine,
			&(openSlOut->outputPlayerObject), &outputAudioSrc, &audioSnk, 3,
			ids1, req1);

	// realize the output player
	(*(openSlOut->outputPlayerObject))->Realize(openSlOut->outputPlayerObject,
			SL_BOOLEAN_FALSE);

	// get the play interface
	(*(openSlOut->outputPlayerObject))->GetInterface(
			openSlOut->outputPlayerObject, SL_IID_PLAY,
			&(openSlOut->outputPlayerPlay));

	// get the pitch interface
	(*(openSlOut->outputPlayerObject))->GetInterface(
			openSlOut->outputPlayerObject, SL_IID_PLAYBACKRATE,
			&(openSlOut->outputPlayerPitch));

	//if (openSlOut->outputPlayerPitch)
	//(*(openSlOut->outputPlayerPitch))->SetRate(openSlOut->outputPlayerPitch, 1000);

	// get the mute/solo interface
	(*(openSlOut->outputPlayerObject))->GetInterface(
			openSlOut->outputPlayerObject, SL_IID_MUTESOLO,
			&(openSlOut->outputPlayerMuteSolo));

	// get the buffer queue interface for output
	(*(openSlOut->outputPlayerObject))->GetInterface(
			openSlOut->outputPlayerObject, SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
			&(openSlOut->outputBufferQueue));

	// register callback on the buffer queue
	(*openSlOut->outputBufferQueue)->RegisterCallback(
			openSlOut->outputBufferQueue, bufferQueueCallback, openSlOut);

	// set the player's state to playing
	(*(openSlOut->outputPlayerPlay))->SetPlayState(openSlOut->outputPlayerPlay,
			SL_PLAYSTATE_PLAYING);

	return JNI_TRUE;
}

// shut down the native audio system
void Java_com_kh_beatbot_BeatBotActivity_shutdown(JNIEnv *env, jclass clazz) {
	freeTracks();
	// destroy output mix object, and invalidate all associated interfaces
	if (outputMixObject != NULL) {
		(*outputMixObject)->Destroy(outputMixObject);
		outputMixObject = NULL;
	}

	// destroy engine object, and invalidate all associated interfaces
	if (engineObject != NULL) {
		(*engineObject)->Destroy(engineObject);
		engineObject = NULL;
		engineEngine = NULL;
	}
}

/****************************************************************************************
 Local versions of playTrack and stopTrack, to be called by the native MIDI ticker
 ****************************************************************************************/

/****************************************************************************************
 Java PlaybackManager JNI methods
 ****************************************************************************************/
void Java_com_kh_beatbot_manager_PlaybackManager_armAllTracks(JNIEnv *env,
		jclass clazz) {
	int i;
	// arm each track, and
	// trigger buffer queue callback to begin writing data to tracks
	for (i = 0; i < trackCount; i++) {
		Track *track = getTrack(env, clazz, i);
		track->armed = true;
	}
	openSlOut->armed = openSlOut->anyTrackArmed = true;
	// start writing zeros to the track's audio out
	bufferQueueCallback(openSlOut->outputBufferQueue, NULL);
}

void Java_com_kh_beatbot_manager_PlaybackManager_armTrack(JNIEnv *env,
		jclass clazz, jint trackNum) {
	Track *track = getTrack(env, clazz, trackNum);
	track->armed = openSlOut->anyTrackArmed = true;
	// start writing zeros to the track's audio out
	bufferQueueCallback(openSlOut->outputBufferQueue, NULL);
}

void Java_com_kh_beatbot_manager_PlaybackManager_disarmAllTracks(JNIEnv *env,
		jclass clazz) {
	int i;
	for (i = 0; i < trackCount; i++) {
		getTrack(env, clazz, i)->armed = false;
	}
	openSlOut->armed = openSlOut->anyTrackArmed = false;
}

void Java_com_kh_beatbot_manager_PlaybackManager_disarmTrack(JNIEnv *env,
		jclass clazz, jint trackNum) {
	getTrack(env, clazz, trackNum)->armed = openSlOut->anyTrackArmed = false;
}

void Java_com_kh_beatbot_manager_PlaybackManager_playTrack(JNIEnv *env,
		jclass clazz, jint trackNum) {
	previewTrack(trackNum);
}

void Java_com_kh_beatbot_manager_PlaybackManager_stopTrack(JNIEnv *env,
		jclass clazz, jint trackNum) {
	stopPreviewingTrack(trackNum);
}

int getSoloingTrackNum() {
	int i;
	for (i = 0; i < NUM_TRACKS; i++) {
		if (getTrack(NULL, NULL, i)->solo) {
			return i;
		}
	}
	return -1;
}

void Java_com_kh_beatbot_manager_PlaybackManager_muteTrack(JNIEnv *env,
		jclass clazz, jint trackNum) {
	Track *track = getTrack(env, clazz, trackNum);
	track->shouldSound = false;
	track->mute = true;
}

void Java_com_kh_beatbot_manager_PlaybackManager_unmuteTrack(JNIEnv *env,
		jclass clazz, jint trackNum) {
	Track *track = getTrack(env, clazz, trackNum);
	int soloingTrackNum = getSoloingTrackNum();
	if (soloingTrackNum == -1 || soloingTrackNum == trackNum)
		track->shouldSound = true;
	track->mute = false;
}

void Java_com_kh_beatbot_manager_PlaybackManager_soloTrack(JNIEnv *env,
		jclass clazz, jint trackNum) {
	Track *track = getTrack(env, clazz, trackNum);
	track->solo = true;
	int i;
	if (!track->mute) {
		track->shouldSound = true;
	}
	for (i = 0; i < NUM_TRACKS; i++) {
		if (i != trackNum) {
			track = getTrack(env, clazz, i);
			track->shouldSound = false;
			track->solo = false;
		}
	}
}

void Java_com_kh_beatbot_manager_PlaybackManager_unsoloTrack(JNIEnv *env,
		jclass clazz, jint trackNum) {
	int i;
	Track *track = getTrack(env, clazz, trackNum);
	track->solo = false;
	for (i = 0; i < NUM_TRACKS; i++) {
		track = getTrack(env, clazz, i);
		if (!track->mute) {
			track->shouldSound = true;
		}
	}

}

/****************************************************************************************
 Java MidiManager JNI methods
 ****************************************************************************************/

void Java_com_kh_beatbot_manager_MidiManager_addMidiNote(JNIEnv *env,
		jclass clazz, jint trackNum, jlong onTick, jlong offTick, jfloat volume,
		jfloat pan, jfloat pitch) {
	Track *track = getTrack(env, clazz, trackNum);
	MidiEvent *event = initEvent(onTick, offTick, volume, pan, pitch);

	addEvent(track, event);
	updateNextEvent(track);
}

void Java_com_kh_beatbot_manager_MidiManager_deleteMidiNote(JNIEnv *env,
		jclass clazz, jint trackNum, jlong tick) {
	Track *track = getTrack(env, clazz, trackNum);
	removeEvent(track, tick, false);
	updateNextEvent(track);
}

void Java_com_kh_beatbot_manager_MidiManager_moveMidiNoteTicks(JNIEnv *env,
		jclass clazz, jint trackNum, jlong prevOnTick, jlong newOnTick,
		jlong newOffTick) {
	Track *track = getTrack(env, clazz, trackNum);
	MidiEvent *event = findEvent(track, prevOnTick);
	if (event != NULL) {
		event->onTick = newOnTick;
		event->offTick = newOffTick;
	}
	updateNextEvent(track);
}

void Java_com_kh_beatbot_manager_MidiManager_moveMidiNote(JNIEnv *env,
		jclass clazz, jint trackNum, jlong tick, jint newTrackNum) {
	if (trackNum < 0|| trackNum >= NUM_TRACKS ||
	newTrackNum < 0 || newTrackNum >= NUM_TRACKS) {return
;	}
	Track *prevTrack = getTrack(env, clazz, trackNum);
	Track *newTrack = getTrack(env, clazz, newTrackNum);
	MidiEvent *event = findEvent(prevTrack, tick);
	if (event != NULL) {
		float volume = event->volume;
		float pan = event->pan;
		float pitch = event->pitch;
		int onTick = event->onTick;
		int offTick = event->offTick;
		long currTick = sampleToTick(currSample);
		if (prevTrack->playing && currTick >= onTick && currTick <= offTick) {
			stopTrack(&tracks[trackNum]);
		}
		removeEvent(prevTrack, tick, false);
		MidiEvent *newEvent = initEvent(onTick, offTick, volume, pan, pitch);
		addEvent(newTrack, newEvent);
	}
	updateNextEvent(prevTrack);
	updateNextEvent(newTrack);
}

void Java_com_kh_beatbot_manager_MidiManager_setNoteMute(JNIEnv *env,
		jclass clazz, jint trackNum, jlong tick, jboolean muted) {
	Track *track = getTrack(env, clazz, trackNum);
	MidiEvent *event = findEvent(track, tick);
	event->muted = muted;
}

void Java_com_kh_beatbot_manager_MidiManager_clearMutedNotes(JNIEnv *env,
		jclass clazz) {
	int i;
	for (i = 0; i < NUM_TRACKS; i++) {
		Track *track = getTrack(env, clazz, i);
		removeEvent(track, -1, true);
		updateNextEvent(track);
	}
}

/****************************************************************************************
 Java MidiNote JNI methods
 ****************************************************************************************/
void Java_com_kh_beatbot_midi_MidiNote_setVolume(JNIEnv *env, jclass clazz,
		jint trackNum, jlong onTick, jfloat volume) {
	Track *track = getTrack(env, clazz, trackNum);
	MidiEvent *event = findEvent(track, onTick);
	if (event != NULL) {
		event->volume = volume;
	}
}

void Java_com_kh_beatbot_midi_MidiNote_setPan(JNIEnv *env, jclass clazz,
		jint trackNum, jlong onTick, jfloat pan) {
	Track *track = getTrack(env, clazz, trackNum);
	MidiEvent *event = findEvent(track, onTick);
	if (event != NULL) {
		event->pan = pan;
	}
}

void Java_com_kh_beatbot_midi_MidiNote_setPitch(JNIEnv *env, jclass clazz,
		jint trackNum, jlong onTick, jfloat pitch) {
	Track *track = getTrack(env, clazz, trackNum);
	MidiEvent *event = findEvent(track, onTick);
	if (event != NULL) {
		event->pitch = pitch;
	}
}

/****************************************************************************************
 Java SampleEditActivity JNI methods
 ****************************************************************************************/

void Java_com_kh_beatbot_SampleEditActivity_setPrimaryVolume(JNIEnv *env,
		jclass clazz, jint trackNum, jfloat volume) {
	Track *track = getTrack(env, clazz, trackNum);
	track->primaryVolume = volume;
	track->volPan->set(track->volPan->config,
			track->primaryVolume * track->noteVolume,
			track->primaryPan * track->notePan);
}

void Java_com_kh_beatbot_SampleEditActivity_setPrimaryPan(JNIEnv *env,
		jclass clazz, jint trackNum, jfloat pan) {
	Track *track = getTrack(env, clazz, trackNum);
	track->primaryPan = pan;
	track->volPan->set(track->volPan->config,
			track->primaryVolume * track->noteVolume,
			track->primaryPan * track->notePan);
}

void Java_com_kh_beatbot_SampleEditActivity_setPrimaryPitch(JNIEnv *env,
		jclass clazz, jint trackNum, jfloat pitch) {
	Track *track = getTrack(env, clazz, trackNum);
	track->primaryPitch = pitch;
	//if (track->outputPlayerPitch != NULL) {
	//(*(track->outputPlayerPitch))->SetRate(track->outputPlayerPitch, (short)((track->pitch + track->primaryPitch)*750 + 500));
	//}
}
