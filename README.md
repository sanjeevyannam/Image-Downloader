# Image-Downloader

Due to background limitations of recent Android O, its good to use job schedular to launch any background operations.

From main activity , start a job schedular service.
When user enters URL in EditBox, and tap on start Button , create a job with url and few parameters then schedule it.
To get the status of the downloading image from service, create an handler in activity and pass it to service.

Inside job service, create 2 handler threads as the job service run in a main thread, one is for downloading the images and one is for updating the download percentage.

When job is started , retrive the url and pass it to the downloadThread to download images.
For downloading image use Rest api , and if the status is OK, start checking the status of downloading image through statusThreadHandler for every 
minute.

When the image is downloaded, stop the corresponding job.

If there are no pending jobs and App is not in foreground job service will be automatically destroyed.
Even when Activity is closed , stop the job service, which will not affect already scheduled jobs.

Note : Get a EXTERNAL_STORAGE_PERMISSION to store images in sdcard.
