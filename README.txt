SMART TRAFFIC MONITORING SYSTEM (Java / IntelliJ)

1. Create the YOLO model file once (needs Python):
       pip install ultralytics
       yolo export model=yolov8n.pt format=onnx imgsz=640
   Copy the resulting yolov8n.onnx into the project root folder.

2. Put a traffic video (e.g. traffic.mp4) in the project root too.

3. IntelliJ: File > Open > select this folder (pom.xml). Wait for Maven to download dependencies.
   Use JDK 17 or newer.

4. Run > Edit Configurations > Application > Main class: traffic.Main
   Program arguments:  --source=traffic.mp4 --model=yolov8n.onnx --ppm=20 --limit=60 --radar=sim
   Working directory: the project root.

5. Press 'q' in the video window to stop. Results are saved in traffic_log.csv.
