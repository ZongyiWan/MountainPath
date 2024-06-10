The java directory, PBAS, contains the algorithm itself. The python script is for visualization. 

Java and Python script connect with port 5001. If this port is used on your machine, please change it to an available port.

If you only want the final path, turn ANIMATE to false in BAS_Server.java. Run the python script run_with_java.py

If you also want a demonstration of how the algorithm works step by step, turn ANIMATE to true in BAS_Server.java. Run the python script animate.py

**Please note that the animation takes a long time to complete when the input map is large. Running animation on a map of size less that 50*50 is recommended.**
