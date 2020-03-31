# http-server-android
Simple http server for android. Serves data from external sd card. 

Two modes - foreground service or activity

Logging for activity and for service (when control activity is running)
Can restrict max threads for http requests

Supports:

/camera/stream for camera streaming (by preview but switchable in code)
/cgi-bin for user commands
images,
html pages,
directory listings...
