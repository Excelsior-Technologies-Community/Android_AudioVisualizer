package com.ext.audiovisualizer.view

enum class VisualizerStyle {
    BAR,
    WAVE,
    CIRCLE,
    STACKED_BARS;

    companion object {
        fun fromIndex(index: Int): VisualizerStyle {
            return values().getOrElse(index) { BAR }
        }
    }
}

