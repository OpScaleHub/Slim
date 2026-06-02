package com.opscalehub.slim;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0007\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\u0011B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\b\u001a\u0004\u0018\u00010\u00052\u0006\u0010\t\u001a\u00020\u0005J\u000e\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u0007J\u000e\u0010\r\u001a\u00020\u000b2\u0006\u0010\t\u001a\u00020\u0005J\u0006\u0010\u000e\u001a\u00020\u000bJ\u0016\u0010\u000f\u001a\u00020\u000b2\u0006\u0010\t\u001a\u00020\u00052\u0006\u0010\u0010\u001a\u00020\u0005R\u001a\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0006\u001a\u0004\u0018\u00010\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0012"}, d2 = {"Lcom/opscalehub/slim/NotificationRegistry;", "", "()V", "activeNotifications", "", "", "listener", "Lcom/opscalehub/slim/NotificationRegistry$NotificationUpdateListener;", "getNotificationPreview", "packageName", "registerListener", "", "l", "removeNotification", "unregisterListener", "updateNotification", "previewText", "NotificationUpdateListener", "app_debug"})
public final class NotificationRegistry {
    @org.jetbrains.annotations.NotNull()
    private static final java.util.Map<java.lang.String, java.lang.String> activeNotifications = null;
    @org.jetbrains.annotations.Nullable()
    private static com.opscalehub.slim.NotificationRegistry.NotificationUpdateListener listener;
    @org.jetbrains.annotations.NotNull()
    public static final com.opscalehub.slim.NotificationRegistry INSTANCE = null;
    
    private NotificationRegistry() {
        super();
    }
    
    public final void registerListener(@org.jetbrains.annotations.NotNull()
    com.opscalehub.slim.NotificationRegistry.NotificationUpdateListener l) {
    }
    
    public final void unregisterListener() {
    }
    
    public final void updateNotification(@org.jetbrains.annotations.NotNull()
    java.lang.String packageName, @org.jetbrains.annotations.NotNull()
    java.lang.String previewText) {
    }
    
    public final void removeNotification(@org.jetbrains.annotations.NotNull()
    java.lang.String packageName) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getNotificationPreview(@org.jetbrains.annotations.NotNull()
    java.lang.String packageName) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0010\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\b\u0010\u0002\u001a\u00020\u0003H&\u00a8\u0006\u0004"}, d2 = {"Lcom/opscalehub/slim/NotificationRegistry$NotificationUpdateListener;", "", "onNotificationsChanged", "", "app_debug"})
    public static abstract interface NotificationUpdateListener {
        
        public abstract void onNotificationsChanged();
    }
}