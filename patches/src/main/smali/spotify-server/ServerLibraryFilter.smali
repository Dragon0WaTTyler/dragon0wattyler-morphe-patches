.class public final Lapp/spicetify/extension/spotify/localserver/nativebridge/ServerLibraryFilter;
.super Lp/vmb0;
.implements Lapp/spicetify/extension/spotify/localserver/ServerFilterMarker;

.field public static final CREATOR:Landroid/os/Parcelable$Creator;

.method static constructor <clinit>()V
    .locals 1
    new-instance v0, Lapp/spicetify/extension/spotify/localserver/nativebridge/ServerFilterCreator;
    invoke-direct {v0}, Lapp/spicetify/extension/spotify/localserver/nativebridge/ServerFilterCreator;-><init>()V
    sput-object v0, Lapp/spicetify/extension/spotify/localserver/nativebridge/ServerLibraryFilter;->CREATOR:Landroid/os/Parcelable$Creator;
    return-void
.end method

.method public constructor <init>(Ljava/util/List;)V
    .locals 0
    invoke-direct {p0, p1}, Lp/vmb0;-><init>(Ljava/util/List;)V
    return-void
.end method

.method public getId()Ljava/lang/String;
    .locals 1
    const-string v0, "spicetify:server"
    return-object v0
.end method

.method public equals(Ljava/lang/Object;)Z
    .locals 1
    instance-of v0, p1, Lapp/spicetify/extension/spotify/localserver/ServerFilterMarker;
    return v0
.end method

.method public hashCode()I
    .locals 1
    const v0, 0x53504943
    return v0
.end method
