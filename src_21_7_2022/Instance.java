import java.util.ArrayList;
import java.util.HashMap;

public class Instance {
    public String InstName;
    public int numVehicles;
    public double capVehicles;
    //public double Q;
    //public double SGS_r;
    public double refuellingRate; // time for unit of power
    //public double SGS_v;
    public int NumRS;
    public int numNodes;
    public String[] NodeId;
    public String[] Type;
    public Integer[] XCoord ;
    public Integer[] YCoord ;
    public Integer[] Demand ;
    public Integer[] ReadyTime ;
    public Integer[] DueDate ;
    public Integer[] ServiceTime;

    public int NumTechs;
    public String[] TechId;
    public Double[] RecSpeed;
    public Double[] EnCost;

    public HashMap<String, ArrayList<String>> RSTech;

    public double phi; // Additional ECR for one unit of load
    public double sigma; // Minimum remaining power (%) when vehicle returns to depot
    public double gamma; // Maximum battery percentage for linear recharges
    public double CL; // Load capacity of vehicle
    public double CB; // Battery capacity of vehicle (kWh)
    public double vmin; // Minimum speed (km/h)
    public double vmax; // Maximum speed (km/h)
    public double F; // Fixed cost per electric vehicle plus driver (RMB)
    public double fe; // Cost of consuming battery energy per unit (RMB/kWh)
    public double fd; // Driver’s variable wage rate (RMB/m)
    public double K; // Slope of the surrogate line of ECR function (ECR = K*Vel + B)
    public double B; // Intercept of the surrogate line of ECR function
    public int NumOfLinesTimeSpeedApp; // number of lines for the time-speed approximation t_ij = D_ij*(k_p*v_ij + b_p)
    public double[] TimeSpeedAppKP;
    public double[] TimeSpeedAppBP;
}
