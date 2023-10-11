import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class InstanceReaderGeneratorTech {
    @Override
    public String toString() {
        // Return a string representation of the object here
        return "InstanceReaderGeneratorTech";
    }
	 ArrayList<String> NodeId = new ArrayList<>();
     ArrayList<Integer> Demand = new ArrayList<>();
     ArrayList<Integer> DueDate = new ArrayList<>();
     ArrayList<Integer> ReadyTime = new ArrayList<>();
     ArrayList<Integer> ServiceTime = new ArrayList<>();
     ArrayList<Integer> XCoord = new ArrayList<>();
     ArrayList<Integer> YCoord = new ArrayList<>();
     ArrayList<String> Type = new ArrayList<>();

     ArrayList<String> TechId = new ArrayList<>();
     ArrayList<Double> RecSpeed = new ArrayList<>();
     ArrayList<Double> EnCost = new ArrayList<>();

     HashMap<String, ArrayList<String>> RSTech = new HashMap<>();

     Instance Inst = new Instance();
     int NumRS=0;
     int numNodes=0;
    int NumVehicles=0;

    public  void generate(String[] paramArgs) throws IOException {
        if(paramArgs.length<13) {
            System.out.println("Usage: InstanceReaderGenerator filetype input.txt techStat.txt parametri.txt SpeedApprx.txt " +
                    "numVeic tmax k k' seed timelimitKdeg TimelimitIter FinalTimelimit kdeg/CW noverb/verb");
            System.exit(0);
        }

        System.out.print("Lettura dati ...");
        // String NomeFileInput = paramArgs[1];
        String filetype = paramArgs[0];
        List<String> lines = Files.readAllLines(Paths.get(paramArgs[1]));
        Integer DueDepot = 0;
        Integer ReadyDepot = 0;
       // Integer NewRndRS = Integer.parseInt(paramArgs[4]);
        Inst.NumRS = 0;
        Inst.numNodes = 0;
        String str[] = null;

        if (filetype.equals("SGS")) {
            Inst.numVehicles = Integer.parseInt(paramArgs[5]);
            NumVehicles = Integer.parseInt(paramArgs[5]);
        }

        int fileTable = 0;
        int currLine = 0;
        for(int numLines = 0;numLines<lines.size();numLines++) {
            if (lines.get(numLines).length() > 0) {
                str = Arrays.asList(lines.get(numLines).split("[\\s+]")).stream().filter(s -> !s.isEmpty()).collect(Collectors.toList()).toArray(new String[0]);

                if (str.length == 0)
                    continue;

                if (filetype.equals("SGS")) {
                    if (numLines > 0 && !str[0].equals("Vehicle") && !str[0].equals("Q")) {
                    //if (numLines > 0 && fileTable == 0) { // Processa nodi
                        // if (numLines > 0 && !str[0].equals("TechId") && !str[0].equals("SiD")) {
                        //if (!str[0].equals("TechId") && !str[0].equals("SiD")) {
                            NodeId.add(str[0]);
                            Type.add(str[1]);
                            Double d = Double.parseDouble(str[2]);
                            XCoord.add(d.intValue());
                            d = Double.parseDouble(str[3]);
                            YCoord.add(d.intValue());
                            d = Double.parseDouble(str[4]);
                            Demand.add(d.intValue());
                            d = Double.parseDouble(str[5]);
                            ReadyTime.add(d.intValue());
                            d = Double.parseDouble(str[6]);
                            DueDate.add(d.intValue());
                            d = Double.parseDouble(str[7]);
                            ServiceTime.add(d.intValue());
                            if (str[1].equals("f")) Inst.NumRS++;
                            else Inst.numNodes++;
                        }
                 else if (str[0].equals("Vehicle")||str[0].equals("Q"))
                    break;
            }
                    /* else
                            fileTable = 1;
                    } else if (numLines > 0 && fileTable == 1) { // Processa tecnologie
                        //   } else if (str[0].equals("Vehicle")||str[0].equals("Q"))
                        if (!str[0].equals("SiD")) {
                            TechId.add((str[0]));
                            Double d = Double.parseDouble(str[1]);
                            RecSpeed.add(d);
                            d = Double.parseDouble(str[2]);
                            EnCost.add(d);
                        } else
                            fileTable = 2;
                    } else if (numLines > 0 && fileTable == 2) { // Processa stazioni

                        if (!str[0].equals("Q")) {
                            ArrayList<String> ListT = new ArrayList<>();
                            for (int lt=0;lt<str.length-1;lt++)
                                ListT.add(str[lt+1]);
                            RSTech.put(str[0],ListT);
                        } else
                            break;
                    }
                } */

              else if (filetype.equals("Cloneless")) {
                    if (currLine == 0) {
                        Inst.InstName = str[0];
                        currLine++;
                    } else if (currLine == 3) {
                        Inst.numVehicles = Integer.parseInt(str[0]);
                        Inst.capVehicles = Integer.parseInt(str[1]);
                        currLine++;
                    } else if (currLine >= 6) {
                        currLine++;
                        NodeId.add(str[0]);
                        Type.add(str[1]);
                        if (str[1].equals("f"))
                            Inst.NumRS++;
                        else
                            Inst.numNodes++;
                        XCoord.add(Integer.parseInt(str[2]));
                        YCoord.add(Integer.parseInt(str[3]));
                        Demand.add(Integer.parseInt(str[4]));
                        ReadyTime.add(Integer.parseInt(str[5]));
                        DueDate.add(Integer.parseInt(str[6]));
                        ServiceTime.add(Integer.parseInt(str[7]));
                    } else
                        currLine++;
                }
                else if(filetype.equals("Solomon"))
                {
                    if(currLine==0)
                    {
                        Inst.InstName = str[0];
                        currLine++;
                    }
                    else if (currLine==3)
                    {
                        Inst.numVehicles = Integer.parseInt(str[0]);
                        Inst.CL = Integer.parseInt(str[1]);
                        currLine++;
                    }
                    else if (currLine>=6) {
                        int ind = Integer.parseInt(str[0]);
                        Inst.numNodes++;
                        NodeId.add(str[0]);
                        XCoord.add(Integer.parseInt(str[1]));
                        YCoord.add(Integer.parseInt(str[2]));
                        Demand.add(Integer.parseInt(str[3]));
                        ReadyTime.add(Integer.parseInt(str[4]));
                        DueDate.add(Integer.parseInt(str[5]));
                        ServiceTime.add(Integer.parseInt(str[6]));
                        if (ind == 0) {
                            Type.add("d");
                            DueDepot = Integer.parseInt(str[5]);
                            ReadyDepot = Integer.parseInt(str[4]);
                        }
                        else
                            Type.add("c");
                    }
                    else
                        currLine++;
                }
                else if(filetype.equals("Cinese"))
                {
                    // cinesi
                    if (currLine == 0) {
                        Inst.InstName = paramArgs[1];
                        Inst.numVehicles = 0;
                        Inst.capVehicles = 0;
                        currLine++;
                    }
                    else if (currLine >= 2) {
                        currLine++;
                        if(str[0].equals(";"))
                            break;
                        Inst.numNodes++;
                        int ind = Integer.parseInt(str[0]);
                        NodeId.add(str[0]);
                        XCoord.add(Integer.parseInt(str[1]));
                        YCoord.add(Integer.parseInt(str[2]));
                        Demand.add(Integer.parseInt(str[3]));
                        ReadyTime.add(Integer.parseInt(str[4]));
                        DueDate.add(Integer.parseInt(str[5]));
                        ServiceTime.add(Integer.parseInt(str[6]));
                        if (ind == 0) {
                            Type.add("d");
                            DueDepot = Integer.parseInt(str[5]);
                            ReadyDepot = Integer.parseInt(str[4]);
                        }
                        else
                            Type.add("c");
                    } else
                        currLine++;
                }
            }
        }

        numNodes = Inst.numNodes;
        NumRS = Inst.NumRS;
        Inst.NodeId = new String[NodeId.size()];
        NodeId.toArray(Inst.NodeId);
        Inst.Demand= new Integer[Demand.size()];
        Demand.toArray(Inst.Demand);
        Inst.DueDate = new Integer[DueDate.size()];
        DueDate.toArray(Inst.DueDate);
        Inst.ReadyTime = new Integer[ReadyTime.size()];
        ReadyTime.toArray(Inst.ReadyTime);
        Inst.ServiceTime = new Integer[ServiceTime.size()];
        ServiceTime.toArray(Inst.ServiceTime);
        Inst.XCoord = new Integer[XCoord.size()];
        XCoord.toArray(Inst.XCoord);
        Inst.YCoord = new Integer[YCoord.size()];
        YCoord.toArray(Inst.YCoord);
        Inst.Type = new String[Type.size()];
        Type.toArray(Inst.Type);


        System.out.println("File "+paramArgs[1]+" letto.");
        lines = Files.readAllLines(Paths.get(paramArgs[2]));
        for(int numLines = 0;numLines<lines.size();numLines++) {
            str = Arrays.asList(lines.get(numLines).split("[\\s+]")).stream().filter(s -> !s.isEmpty()).collect(Collectors.toList()).toArray(new String[0]);

            if (str.length == 0)
                continue;

            if (numLines > 0 && fileTable == 0) { // Processa tecnologie
            if (!str[0].equals("SiD")) {
                TechId.add((str[0]));
                Double d = Double.parseDouble(str[1]);
                RecSpeed.add(d);
                d = Double.parseDouble(str[2]);
                EnCost.add(d);
            } else
                fileTable = 1;
        } else if (numLines > 0 && fileTable == 1) { // Processa stazioni

            //if (!str[0].equals("Q")) {
                ArrayList<String> ListT = new ArrayList<>();
                for (int lt=0;lt<str.length-1;lt++)
                    ListT.add(str[lt+1]);
                RSTech.put(str[0],ListT);
            //} else
            //    break;
        }
    }

        Inst.NumTechs = TechId.size();
        Inst.TechId = TechId.toArray(new String[0]);
        Inst.RecSpeed = RecSpeed.toArray(new Double[0]);
        Inst.EnCost = EnCost.toArray(new Double[0]);
        Inst.RSTech = new HashMap<>(RSTech);

        lines = Files.readAllLines(Paths.get(paramArgs[3]));
        for(int numLines = 0;numLines<lines.size();numLines++) {
            if (lines.get(numLines).length() > 0) {
                //String str[] = lines.get(i).split(" ");//,|\\t|;| |/");
                str = Arrays.asList(lines.get(numLines).split("[ ]")).stream().filter(s -> !s.isEmpty()).collect(Collectors.toList()).toArray(new String[0]);
                switch (numLines) {
                    case 0:
                        Inst.phi = Double.parseDouble(str[1]);
                        break;
                    case 1:
                        Inst.sigma = Double.parseDouble(str[1]);
                        break;
                    case 2:
                        Inst.gamma = Double.parseDouble(str[1]);
                        break;
                    case 3:
                        Inst.CL = Double.parseDouble(str[1]);
                        break;
                    case 4:
                        Inst.CB = Double.parseDouble(str[1]);
                        break;
                    case 5:
                        Inst.vmin = Double.parseDouble(str[1]);
                        break;
                    case 6:
                        Inst.vmax = Double.parseDouble(str[1]);
                        break;
                    case 7:
                        Inst.F = Double.parseDouble(str[1]);
                        break;
                    case 8:
                        Inst.fe = Double.parseDouble(str[1]);
                        break;
                    case 9:
                        Inst.fd = Double.parseDouble(str[1]);
                        break;
                    case 10:
                        Inst.K = Double.parseDouble(str[1]);
                        break;
                    case 11:
                        Inst.B = Double.parseDouble(str[1]);
                        break;
                    default:
                        break;
                }
            }
        }

        System.out.println("File "+paramArgs[3]+" letto.");

        lines = Files.readAllLines(Paths.get(paramArgs[4]));
        Inst.NumOfLinesTimeSpeedApp = lines.size();
        Inst.TimeSpeedAppBP = new double[Inst.NumOfLinesTimeSpeedApp];
        Inst.TimeSpeedAppKP = new double[Inst.NumOfLinesTimeSpeedApp];

        for(int numLines = 0;numLines<lines.size();numLines++) {
            if (lines.get(numLines).length() > 0) {
                //String str[] = lines.get(i).split(" ");//,|\\t|;| |/");
                str = Arrays.asList(lines.get(numLines).split("[ ]")).stream().filter(s -> !s.isEmpty()).collect(Collectors.toList()).toArray(new String[0]);
                Inst.TimeSpeedAppKP[numLines] = Double.parseDouble(str[1]);
                Inst.TimeSpeedAppBP[numLines] = Double.parseDouble(str[2]);
            }
        }
        System.out.println("File "+paramArgs[4]+" letto.");

    }

}

