/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

public final class GroupVideo {
    public final String id, channelId, title, channelName, thumbnail;
    public final long published, views;
    public final String avatar;
    public final VideoMetadata metadata;
    public GroupVideo(String id, String channel, String title, String author, String thumbnail, long published, long views) {
        this(id, channel, title, author, thumbnail, published, views, "", VideoMetadata.UNKNOWN);
    }
    public GroupVideo(String id, String channel, String title, String author, String thumbnail, long published, long views,
                      String avatar, VideoMetadata metadata) {
        if (!id.matches("[A-Za-z0-9_-]{11}") || !GroupBackup.isChannelId(channel)) throw new IllegalArgumentException("Invalid video identity");
        this.id = id; channelId = channel; this.title = title; channelName = author;
        this.thumbnail = thumbnail; this.published = published; this.views = views;
        this.avatar = avatar == null ? "" : ChannelPageMetadata.safeAvatar(avatar);
        this.metadata = metadata == null ? VideoMetadata.UNKNOWN : metadata;
    }
    public GroupVideo withMetadata(String avatar, VideoMetadata metadata) {
        return new GroupVideo(id, channelId, title, channelName, thumbnail, published, views, avatar, metadata);
    }
}
