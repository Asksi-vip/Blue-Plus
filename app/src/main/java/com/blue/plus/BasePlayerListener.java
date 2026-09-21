package com.blue.plus;

import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.video.VideoSize;
import java.util.List;

public class BasePlayerListener implements Player.Listener {
    @Override public void onAudioAttributesChanged(AudioAttributes audioAttributes) {}
    @Override public void onAudioSessionIdChanged(int i) {}
    @Override public void onAvailableCommandsChanged(Player.Commands commands) {}
    @Override public void onCues(CueGroup cueGroup) {}
    @Deprecated @Override public void onCues(List<com.google.android.exoplayer2.text.Cue> list) {}
    @Override public void onDeviceInfoChanged(DeviceInfo deviceInfo) {}
    @Override public void onDeviceVolumeChanged(int i, boolean z) {}
    @Override public void onEvents(Player player, Player.Events events) {}
    @Override public void onIsLoadingChanged(boolean z) {}
    @Override public void onIsPlayingChanged(boolean z) {}
    @Deprecated @Override public void onLoadingChanged(boolean z) {}
    @Override public void onMaxSeekToPreviousPositionChanged(long j) {}
    @Override public void onMediaItemTransition(MediaItem mediaItem, int i) {}
    @Override public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {}
    @Override public void onMetadata(Metadata metadata) {}
    @Override public void onPlayWhenReadyChanged(boolean z, int i) {}
    @Override public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}
    @Override public void onPlaybackStateChanged(int i) {}
    @Override public void onPlaybackSuppressionReasonChanged(int i) {}
    @Override public void onPlayerError(PlaybackException playbackException) {}
    @Override public void onPlayerErrorChanged(PlaybackException playbackException) {}
    @Deprecated @Override public void onPlayerStateChanged(boolean z, int i) {}
    @Override public void onPlaylistMetadataChanged(MediaMetadata mediaMetadata) {}
    @Deprecated @Override public void onPositionDiscontinuity(int i) {}
    @Override public void onPositionDiscontinuity(Player.PositionInfo positionInfo, Player.PositionInfo positionInfo2, int i) {}
    @Override public void onRenderedFirstFrame() {}
    @Override public void onRepeatModeChanged(int i) {}
    @Override public void onSeekBackIncrementChanged(long j) {}
    @Override public void onSeekForwardIncrementChanged(long j) {}

    @Override public void onShuffleModeEnabledChanged(boolean z) {}
    @Override public void onSkipSilenceEnabledChanged(boolean z) {}
    @Override public void onSurfaceSizeChanged(int i, int i2) {}
    @Override public void onTimelineChanged(Timeline timeline, int i) {}
    @Override public void onTrackSelectionParametersChanged(TrackSelectionParameters trackSelectionParameters) {}
    @Override public void onTracksChanged(Tracks tracks) {}
    @Override public void onVideoSizeChanged(VideoSize videoSize) {}
    @Override public void onVolumeChanged(float f) {}
}
