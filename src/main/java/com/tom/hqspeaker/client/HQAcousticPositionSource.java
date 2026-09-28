package com.tom.hqspeaker.client;

/**
 * Separates the physical speaker position used for acoustic evaluation from the stabilized render position
 * exposed to Minecraft's SoundManager.
 */
interface HQAcousticPositionSource {
    double hqspeaker$physicalX();
    double hqspeaker$physicalY();
    double hqspeaker$physicalZ();

    void hqspeaker$setAcousticPosition(double x, double y, double z);
    void hqspeaker$clearAcousticPosition();
}
