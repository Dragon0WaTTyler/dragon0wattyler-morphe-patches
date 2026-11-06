.class public final Lapp/spicetify/extension/spotify/localserver/nativebridge/ServerFilterCreator;
.super Ljava/lang/Object;
.implements Landroid/os/Parcelable$Creator;

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public createFromParcel(Landroid/os/Parcel;)Ljava/lang/Object;
    .locals 2
    sget-object v0, Lp/vmb0;->CREATOR:Landroid/os/Parcelable$Creator;
    invoke-interface {v0, p1}, Landroid/os/Parcelable$Creator;->createFromParcel(Landroid/os/Parcel;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lp/vmb0;
    iget-object v0, v0, Lp/vmb0;->b:Ljava/util/List;
    new-instance v1, Lapp/spicetify/extension/spotify/localserver/nativebridge/ServerLibraryFilter;
    invoke-direct {v1, v0}, Lapp/spicetify/extension/spotify/localserver/nativebridge/ServerLibraryFilter;-><init>(Ljava/util/List;)V
    return-object v1
.end method

.method public newArray(I)[Ljava/lang/Object;
    .locals 1
    new-array v0, p1, [Lapp/spicetify/extension/spotify/localserver/nativebridge/ServerLibraryFilter;
    return-object v0
.end method
