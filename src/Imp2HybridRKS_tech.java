import ilog.concert.*;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

class Route {
    String start;
    String end;
    String intermediate;

    Route(String start, String end, String intermediate) {
        this.start = start;
        this.end = end;
        this.intermediate = intermediate;
    }
}
/**
 * Archi tra nodi customer e/o deposito
 */
 class Archi{
	 public String idi;
	 public String idj;

	 public Archi( String i,  String j)
	 {
		 this.idi = i;
		 this.idj = j;
	 }
	 @Override
	 public int hashCode() {
		 final int prime = 31;

		 int result = 1;
		 result = prime * result + this.idi.hashCode();
		 result  = result*prime + this.idj.hashCode();
		 return result;
	 }
	 @Override
	 public boolean equals( Object obj) {
		 if (this == obj)
			 return true;
		 if (obj == null)
			 return false;
		 if (getClass() != obj.getClass())
			 return false;
		  Archi other = (Archi) obj;
		 if (idi.equals(other.idi) && idj.equals(other.idj))
			 return true;
		 return false;
	 }
 }

public class Imp2HybridRKS_tech {
//input parameters
public cloneless_tech2 M;
public double T_max;
public int NB_bar, P_max, LB;
public String file_model;
public int finalTimelimit = 0;
public int limiteTkDeg = 0;

//parameters computed
public double TMIP;
public int NB;
public ArrayList<String>K=new ArrayList<String>();
public HashMap<XIndex, Double>reduced_costs=new HashMap<XIndex, Double>();
int conta_k=0;
int conta_not_k=0;

// K alternativo e non_K alternativo
public ArrayList<XIndex> Kalt = new ArrayList<XIndex>();
public ArrayList<XIndex> not_Kalt = new ArrayList<XIndex>();
//public ArrayList<XIndex> ZeroedInK = new ArrayList<XIndex>();
//public HashMap<XIndex, Integer> ZeroInKalt = new HashMap<XIndex, Integer>();

// insiemi di archi usati per la generazione dei bucket
public ArrayList<Archi> Kalt_arcs = new ArrayList<Archi>();
public ArrayList<Archi> not_Kalt_arcs = new ArrayList<Archi>();

//public HashMap<XIndex, Integer> VarXFrequency = new HashMap<XIndex, Integer>();


	public ArrayList<String> usedDepots = new ArrayList<>();

	public ArrayList<XIndex>x_H=new ArrayList<XIndex>();
public ArrayList<XIndex>x_best_H=new ArrayList<XIndex>();
public  HashMap<XIndex, Double>e_H = new HashMap<XIndex, Double>();
public  HashMap<XIndex, Double>t_H = new  HashMap<XIndex, Double>();
public  HashMap<XIndex, Double>v_H = new  HashMap<XIndex, Double>();
public  HashMap<XIndex, Double>ea_H = new HashMap<XIndex, Double>();
public  HashMap<XIndex, Double>ta_H = new  HashMap<XIndex, Double>();
public  HashMap<XIndex, Double>va_H = new  HashMap<XIndex, Double>();
public  HashMap<XIndex, Double>eb_H = new HashMap<XIndex, Double>();
public  HashMap<XIndex, Double>tb_H = new  HashMap<XIndex, Double>();
public  HashMap<XIndex, Double>vb_H = new  HashMap<XIndex, Double>();
public  HashMap<String, Double>r_H = new HashMap<String, Double>();
public  HashMap<String, Double>a_H = new HashMap<String, Double>();
public  HashMap<String, Double>w_H = new HashMap<String, Double>();
public HashMap<XIndex, Double>b_H=new HashMap<XIndex, Double>();
public double z_H;
public  double LowerBound;
public  double gapbest = Double.POSITIVE_INFINITY;
public double z_best_H;
public double CostoVeicoli_H_best;
public double CostoEnergia_H_best;
public double CostoDrivers_H_best;
public double CostoVeicoli_H;
public double CostoEnergia_H;
public double CostoDrivers_H;
public double Gap_H;
public  double timeSoFar = 0;
public int limiteT=10;

public HashMap<XIndex,Integer>P=new HashMap<XIndex,Integer>();
public ArrayList<ArrayList<XIndex>> buckets;
public PrintStream pr=null;
public static PrintStream pr_excel=null;
public static PrintStream pr_out=null;

public PrintStream verboso=null; // usato per abilitare/disabilitare l'output di Cplex

public static double GlobalStartTime =0;

public static PrintStream prRes=null;
public static String nomeIstanza = "";

//oggetto k-degree per implementare la versione ibrida
matheuristic_k_degree_hybridOneGenerator_tech kDegree;
public static PrintStream RandTracker=null;

	/**
	 * Genera l'insieme di archi (i,j) (senza stazioni) Kalt_arcs corrispondenti alle variabili in x = 1 e l'insieme not_Kalt_acrs corrispondente alle var x = 0
	 * @throws IloException
	 */
public void generate_KDeg_arcs() throws IloException {

	Kalt_arcs.clear();
	not_Kalt_arcs.clear();
	for (Map.Entry<XIndex, IloNumVar> varX : kDegree.M.x.entrySet()) {
		if (kDegree.M.model.getValue(varX.getValue()) >= 0.99) {
			if(!Kalt_arcs.contains(new Archi(varX.getKey().xi, varX.getKey().xj)))
				Kalt_arcs.add(new Archi(varX.getKey().xi, varX.getKey().xj));
		}
		if (kDegree.M.model.getValue(varX.getValue()) <= 0.0001) {
			if(!Kalt_arcs.contains(new Archi(varX.getKey().xi, varX.getKey().xj)) && !not_Kalt_arcs.contains(new Archi(varX.getKey().xi, varX.getKey().xj)))
				not_Kalt_arcs.add(new Archi(varX.getKey().xi, varX.getKey().xj));
		}
	}
}

public void generate_KDeg_arcs_xinit() throws IloException {
		Kalt_arcs.clear();
		not_Kalt_arcs.clear();

		for (XIndex varX : kDegree.M.x_init) {
			if (!Kalt_arcs.contains(new Archi(varX.xi, varX.xj)))
				Kalt_arcs.add(new Archi(varX.xi, varX.xj));
		}
		for (XIndex varX: kDegree.M.Xindici) {
				if(!Kalt_arcs.contains(new Archi(varX.xi, varX.xj)) && !not_Kalt_arcs.contains(new Archi(varX.xi, varX.xj)))
					not_Kalt_arcs.add(new Archi(varX.xi, varX.xj));
			}
		}


	/**
	 * Aggiorna gli insiemi di archi (i,j) (senza stazioni) Kalt_arcs e not_Kalt_arcs in base al Kernel aggiornato
	 * @throws IloException
	 */
public void update_KDeg_arcs() throws IloException {

		Kalt_arcs.clear();;
		not_Kalt_arcs.clear();
		for (XIndex varXinK :Kalt) {
				if(!Kalt_arcs.contains(new Archi(varXinK.xi, varXinK.xj)))
					Kalt_arcs.add(new Archi(varXinK.xi, varXinK.xj));
			}
		for (XIndex varXinK :not_Kalt) {
				if(!Kalt_arcs.contains(new Archi(varXinK.xi, varXinK.xj)) && !not_Kalt_arcs.contains(new Archi(varXinK.xi, varXinK.xj)))
					not_Kalt_arcs.add(new Archi(varXinK.xi, varXinK.xj));
			}
	}

	/**
	 * RKS
	 * @return
	 * @throws FileNotFoundException
	 * @throws IloException
	 */
public IloCplex.Status RandKernSearchKDegree() throws FileNotFoundException, IloException {

	firstSolutionKDegree(file_model);
	generate_K_from_sol();
	// rimetto LB per SOC a sigma e azzero var di violazione
	//for(int ind=0; ind<kDegree.M.N.length;ind++) {
	//	kDegree.M.r.get(kDegree.M.N[ind].id).setLB(M.sigma);
	//	kDegree.M.dr.get(kDegree.M.N[ind].id).setUB(0.0);
	//}
	IloCplex.Status stato = solveMIPwithRandomKernel();
	print_final_solution((System.currentTimeMillis() - GlobalStartTime) / 1000.00);
	return stato;

}

	/**
	 * Implementa la MIP finale
	 * @return
	 * @throws FileNotFoundException
	 * @throws IloException
	 */
public IloCplex.Status FinalIteration(String numRun) throws FileNotFoundException, IloException {

	IloCplex.Status stato;
	for(XIndex vx: x_best_H)
	{
		//impongo che la sommatoria di x_ijs (incluso la stazione fittizia) sia 1
		IloLinearNumExpr exp= M.model.linearNumExpr();
		for(Map.Entry<XIndex,IloNumVar> varX: M.x.entrySet())
			if(varX.getKey().xi.equals(vx.xi)&&varX.getKey().xj.equals(vx.xj)){
				exp.addTerm(1, varX.getValue());
			}
		M.model.addEq(exp, 1, "Constr_final_for_pair"+vx.xi+"-->"+vx.xj);
	}

	ArrayList<XIndex>x_aus=new ArrayList<XIndex>();
	for(Map.Entry<XIndex,IloNumVar> varX: M.x.entrySet()) {
		boolean trovato=false;
		for(XIndex vx: x_best_H) {
			if(varX.getKey().xi.equals(vx.xi)&&varX.getKey().xj.equals(vx.xj)) {
				trovato=true;
				break;
			}
		}
		if(!trovato) {
			//M.x.get(varX).setUB(0.0);
			x_aus.add(varX.getKey());
		}
	}
	for(XIndex xin:x_aus) {
		M.x.get(xin).setUB(0.0);
	}

	M.model.setParam(IloCplex.Param.TimeLimit, finalTimelimit);
	M.model.setOut(M.verbCloneless);
	M.model.setWarning(M.verbCloneless);
	double tempoLastMIP = System.currentTimeMillis();
	M.model.solve();
	tempoLastMIP = (System.currentTimeMillis() - tempoLastMIP)/1000.0;
	stato=M.model.getStatus();
	double TempoFinale = (System.currentTimeMillis() - GlobalStartTime) / 1000.00;

	double finalGap = 0.0;
	String Sstato = "Non Def";
	if(stato.equals(IloCplex.Status.Infeasible)) {
		Sstato = "unf";
		finalGap = -1;
	}
	else if (stato.equals(IloCplex.Status.Optimal)) {
		Sstato = "opt";
		finalGap = M.model.getMIPRelativeGap();
	}
	else if (stato.equals(IloCplex.Status.Feasible)) {
		Sstato = "feas";
		finalGap = M.model.getMIPRelativeGap();
	}

	//System.err.println(Sstato);
	pr.println("Solution after final iteration");
	pr.println("Objective value:"+M.model.getObjValue());
	pr.println("Solution:");
	for(XIndex s:M.x.keySet()) {
		if(M.model.getValue(M.x.get(s))>=0.99)
		pr.println("X_"+s.xi+"_"+s.xj+"_"+s.staz);
	}
	pr.println("Total time (in sec) including the final iteration:"+ TempoFinale);
	M.write_output("final_iteration_"+file_model, numRun,false);
	double CostoV = M.model.getValue(M.CostoVeicoli);
	double CostoE = M.model.getValue(M.CostoEnergia);
	double CostoD = M.model.getValue(M.CostoDrivers);
	prRes.println(nomeIstanza+";HyRanKS;FinalMIP;"+M.model.getObjValue()+";"+(int)Math.round(CostoV/M.FC)+";"+CostoV+";"+CostoE+";"+CostoD+";"+ TempoFinale +";"+Sstato+";"+tempoLastMIP+";"+finalGap);
	System.out.println(nomeIstanza+";HyRanKS;FinalMIP;"+M.model.getObjValue()+";"+(int)Math.round(CostoV/M.FC)+";"+CostoV+";"+CostoE+";"+CostoD+";"+ TempoFinale +";"+Sstato+";"+tempoLastMIP+";"+finalGap);
	System.out.println("Tempo final "+tempoLastMIP);
	return stato;

}

	/**
	 * Core della RKS
	 * @return
	 * @throws IloException
	 * @throws FileNotFoundException
	 */
public IloCplex.Status solveMIPwithRandomKernel() throws IloException, FileNotFoundException {
	int iter = 0;
	int iterUnf = 0;
	int totalNotImpr=0;
	IloCplex.Status stato = IloCplex.Status.Infeasible;
	double factor = 2;
	boolean stop = false;
	int origTimeLim = limiteT;
	timeSoFar = (System.currentTimeMillis() - GlobalStartTime) / 1000.00;
    M.model.setParam(IloCplex.Param.TimeLimit, limiteT);

	M.model.setOut(verboso);
	M.model.setWarning(verboso);

	boolean siamoA1800 = false;
	double time_to_best =0;

	while (timeSoFar < T_max ) {
		if(timeSoFar>1800 && !siamoA1800){
			System.out.println("ResInter;"+timeSoFar+";"+z_best_H+";"+CostoVeicoli_H_best+";"+CostoEnergia_H_best+";"+CostoDrivers_H_best);
			pr_out.println("ResInter;"+timeSoFar+";"+z_best_H+";"+CostoVeicoli_H_best+";"+CostoEnergia_H_best+";"+CostoDrivers_H_best);
			prRes.println(nomeIstanza+";HyRanKS;Interm;"+z_best_H+";"+(int)Math.round(CostoVeicoli_H_best/M.FC)+";"+CostoVeicoli_H_best+";"+CostoEnergia_H_best+";"+CostoDrivers_H_best+";"+timeSoFar+";"+time_to_best);
			siamoA1800 = true;
		}

    	// rimetto UB = 0 per le var non in KS
		for(XIndex vx: Kalt)
			M.x.get(vx).setUB(1.0);
		for(XIndex vx: not_Kalt)
			M.x.get(vx).setUB(0.0);

		for (int round = 0; round < 4; round++) {
			timeSoFar = (System.currentTimeMillis() - GlobalStartTime) / 1000.00;

			if(timeSoFar>1800 && !siamoA1800){
				System.out.println("ResInter;"+timeSoFar+";"+z_best_H+";"+CostoVeicoli_H_best+";"+CostoEnergia_H_best+";"+CostoDrivers_H_best);
				pr_out.println("ResInter;"+timeSoFar+";"+z_best_H+";"+CostoVeicoli_H_best+";"+CostoEnergia_H_best+";"+CostoDrivers_H_best);
				prRes.println(nomeIstanza+";HyRanKS;Interm;"+z_best_H+";"+(int)Math.round(CostoVeicoli_H_best/M.FC)+";"+CostoVeicoli_H_best+";"+CostoEnergia_H_best+";"+CostoDrivers_H_best+";"+timeSoFar+";"+time_to_best);
				siamoA1800 = true;
			}

			if(timeSoFar > T_max) {
				stop = true;
				break;
			}
			ArrayList<XIndex> buck = getRandomBucket(factor);
			M.add_constraintBucket(buck, z_H);

			M.model.setParam(IloCplex.Param.TimeLimit, Math.min(limiteT, T_max-timeSoFar));
			M.model.setOut(verboso);
			M.model.setWarning(verboso);

			double startTimeStamp = System.currentTimeMillis();//M.model.getCplexTime();
			M.model.solve();
			double TimeSpent = (System.currentTimeMillis() - startTimeStamp)/1000.0;
			timeSoFar = (System.currentTimeMillis() - GlobalStartTime) / 1000.00;
            stato = M.model.getStatus();

            if (stato == IloCplex.Status.Feasible &&
				M.model.getMIPRelativeGap() > 0.1 &&
			    timeSoFar < T_max )
				{ // se e' fesible ma non ottimo do un altro quanto di tempo per cercare l'ottimalita'
				M.model.setParam(IloCplex.Param.TimeLimit, Math.min(limiteT, T_max-timeSoFar));
                M.model.solve();
                TimeSpent = (System.currentTimeMillis() - startTimeStamp)/1000.0;
                timeSoFar = (System.currentTimeMillis() - GlobalStartTime) / 1000.00;
            }
            //-------------
            boolean impr=false; // se la best so far e' migliorata

            if (stato == IloCplex.Status.Feasible || stato == IloCplex.Status.Optimal) {
				System.out.print(iter+";"+round+";"+factor+";"+TimeSpent+";"+timeSoFar+";"+ stato+";");
				pr_out.print(iter+";"+round+";"+factor+";"+TimeSpent+";"+timeSoFar+";"+ stato+";");
				impr = saveSolution();

				if(impr) {
					iterUnf = 0;
					totalNotImpr = 0;
					time_to_best = timeSoFar;
				}
				else {
					iterUnf++; // conta iterazioni not improving
					totalNotImpr++;
				}
			}
			else {
                iterUnf++;
				totalNotImpr++;
			}

			if(TimeSpent < limiteT) // se risolto entro il quanto di tempo
				factor += 0.25;
			else {
				if (factor > 1)
					factor -= 0.25;
				else
					factor = 1;
			}

			// set UB = 0 for tested bucket
			for (XIndex s : buck) {
				M.x.get(s).setUB(0.0);
			}

			M.delete_constraint();
		}
		if(stop)
			break;

		iter++;

		// ----------------------------------------------------------
		// update Kernel
		for(XIndex vx: Kalt) // incremento a 1 il contatore per le var in KS
			P.put(vx, P.get(vx)+1);
		for(XIndex vx: x_best_H) // azzero il contatore per la var x = 1 nella soluzione
			P.put(vx, 0);

		// metto in Kalt le x che sono =1 nella best corrente o erano in KS negli ultimi run (dopo 3 run che non sono a 1 sparicono dal KS)
		Kalt = new ArrayList<XIndex>(); // svuoto Kalt
		for(Map.Entry<XIndex, Integer> px: P.entrySet()) {
			if(px.getValue()<=3) // 0 1 2 3 + 4 5 6 7
				Kalt.add(px.getKey());
		}

		not_Kalt.clear();
		for (XIndex vx : M.x.keySet())
			if (!Kalt.contains(vx))
				not_Kalt.add(vx);

		update_KDeg_arcs();

        // verifico il massimo numero di iterazioni not impr per la terminazione (valore massimo fissato nel codice = 24)
		if(totalNotImpr>=24)
		{
			System.out.println("RAGGIUNTO LIMITE ITER NOT IMPROVING - STOP;"+timeSoFar);
			pr_out.println("RAGGIUNTO LIMITE ITER NOT IMPROVING - STOP;"+timeSoFar);
			prRes.println(nomeIstanza+";HyRanKS;Final;"+z_best_H+";"+(int)Math.round(CostoVeicoli_H_best/M.FC)+";"+CostoVeicoli_H_best+";"+CostoEnergia_H_best+";"+CostoDrivers_H_best+";"+timeSoFar+";"+time_to_best);
			//prRes.close();
			return stato;
		}

        // se non ho migliorato per almeno 4 iterazioni raddoppio il quanto di tempo
		if(iterUnf>=4){
			iterUnf=0;
			limiteT *= 2;
			M.model.setParam(IloCplex.Param.TimeLimit, limiteT);
		}
		else { // se miglioro riporto il quanto di tempo al valore originale
			limiteT = origTimeLim;
			M.model.setParam(IloCplex.Param.TimeLimit, limiteT);
		}

		timeSoFar = (System.currentTimeMillis() - GlobalStartTime) / 1000.00;
    } // MAIN LOOP END

	// Terminato --------------
	for(XIndex vx: M.x.keySet())
		M.x.get(vx).setUB(1.0);

	System.out.println("TERMINATO;"+timeSoFar);
	pr_out.println("TERMINATO;"+timeSoFar);
	prRes.println(nomeIstanza+";HyRanKS;Final;"+z_best_H+";"+(int)Math.round(CostoVeicoli_H_best/M.FC)+";"+CostoVeicoli_H_best+";"+CostoEnergia_H_best+";"+CostoDrivers_H_best+";"+timeSoFar+";"+time_to_best);

	return stato;
}

public void print_final_solution(double tempoTotale) {
	pr.println("Objective value:"+z_H);
	pr.println("Final solution:");
	for(XIndex s:x_H) {
		pr.println("X_"+s.xi+"_"+s.xj+"_"+s.staz);
	}
	pr.println("Total time (in sec):"+tempoTotale);
}

	/**
	 * Determina la soluzione iniziale risolvendo un MIP in cui le variabili non nulle sono quelle della soluzione
	 * trovata dalla kDegree senza soste insieme a tutti i possibili archi con soste a stazioni
	 * @param file_model
	 * @throws IloException
	 * @throws FileNotFoundException
	 */
public void firstSolutionKDegree(String file_model) throws IloException, FileNotFoundException{

	String name_model = file_model.replace(".txt", "");

	// Aggiungo tutte le var con stazioni associate a quelle degli archi della soluzione iniziale
	for(XIndex xind: kDegree.M.Xindici) {
		if (Kalt_arcs.contains(new Archi(xind.xi, xind.xj))) {
			kDegree.M.x.get(xind).setUB(1.0);
		}
	}

	M.model.setParam(IloCplex.Param.TimeLimit, limiteTkDeg);
	//M.model.setParam(IloCplex.Param.MIP.Limits.Solutions,1);
	M.model.setOut(verboso);
	M.model.setWarning(verboso);
    M.model.exportModel("mod_firstsol.lp");
	boolean solved= false;
	while (!solved) {
	 solved = M.model.solve();
	 if (kDegree.M.model.getStatus().equals(IloCplex.Status.Infeasible)){
		 System.err.println("Starting feasible solution not found");
		 System.exit(0);
	 }
	}

    timeSoFar = (System.currentTimeMillis() - GlobalStartTime) / 1000.00;

	z_best_H = Double.POSITIVE_INFINITY;
	CostoDrivers_H_best = Double.POSITIVE_INFINITY;
	CostoEnergia_H_best = Double.POSITIVE_INFINITY;
	CostoVeicoli_H_best = Double.POSITIVE_INFINITY;
	LowerBound = kDegree.M.model.getBestObjValue();
	gapbest = kDegree.M.model.getMIPRelativeGap();
	System.out.print(";;;;"+timeSoFar+";;");
	pr_out.print(";;;;"+timeSoFar+";;");

	saveKDegreeSolution();
	prRes.println(nomeIstanza+";HyRanKS;Init;"+z_best_H+";"+(int)Math.round(CostoVeicoli_H_best/M.FC)+";"+CostoVeicoli_H_best+";"+CostoEnergia_H_best+";"+CostoDrivers_H_best+";"+timeSoFar+";"+timeSoFar);
}

public boolean saveKDegreeSolution() throws IloException {

		z_H = kDegree.M.model.getObjValue();
		CostoDrivers_H = kDegree.M.model.getValue(M.CostoDrivers);
		CostoEnergia_H = kDegree.M.model.getValue(M.CostoEnergia);
		CostoVeicoli_H = kDegree.M.model.getValue(M.CostoVeicoli);
		Gap_H = kDegree.M.model.getMIPRelativeGap();

		x_H.clear();
		e_H.clear();
		t_H.clear();
		v_H.clear();
		ea_H.clear();
		ta_H.clear();
		va_H.clear();
		eb_H.clear();
		tb_H.clear();
		vb_H.clear();
		a_H.clear();
		r_H.clear();
		w_H.clear();
		b_H.clear();
		for (Map.Entry<XIndex, IloNumVar> varX : kDegree.M.x.entrySet()) {
			if (kDegree.M.model.getValue(varX.getValue()) >= 0.99) {
				x_H.add(varX.getKey());

				if (varX.getKey().staz.equals("fictius")) {
					e_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.e.get(varX.getKey())));
					t_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.t.get(varX.getKey())));
					v_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.v.get(varX.getKey())));

				} else {
					ea_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.ea.get(varX.getKey())));
					ta_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.ta.get(varX.getKey())));
					va_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.va.get(varX.getKey())));

					eb_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.eb.get(varX.getKey())));
					tb_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.tb.get(varX.getKey())));
					vb_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.vb.get(varX.getKey())));

					b_H.put(varX.getKey(), kDegree.M.model.getValue(kDegree.M.b.get(varX.getKey())));
				}
				a_H.put(varX.getKey().xj, kDegree.M.model.getValue(kDegree.M.a.get(varX.getKey().xj)));

				if (!varX.getKey().xj.contains("D")) {
					r_H.put(varX.getKey().xj, kDegree.M.model.getValue(kDegree.M.r.get(varX.getKey().xj)));
					w_H.put(varX.getKey().xj, kDegree.M.model.getValue(kDegree.M.w.get(varX.getKey().xj)));
				}
			}
		}
	    System.out.print(z_H);
		pr_out.print(z_H);
		boolean impr = false;
		if(z_H<z_best_H){
			z_best_H = z_H;
			CostoDrivers_H_best = CostoDrivers_H;
			CostoVeicoli_H_best = CostoVeicoli_H;
			CostoEnergia_H_best = CostoEnergia_H;
			x_best_H.clear();
			x_best_H = new ArrayList<>(x_H);
			System.out.println(";*");
			pr_out.println(";*");
			impr=true;
		}
		else {
			System.out.println();
			pr_out.println();
		}
		return  impr;

}

public boolean saveSolution() throws IloException { //saveSolution(int iter)

	z_H = M.model.getObjValue();
	CostoDrivers_H = M.model.getValue(M.CostoDrivers);
	CostoEnergia_H = M.model.getValue(M.CostoEnergia);
	CostoVeicoli_H = M.model.getValue(M.CostoVeicoli);
	Gap_H = M.model.getMIPRelativeGap();

	x_H.clear();
	e_H.clear();
	t_H.clear();
	v_H.clear();
	ea_H.clear();
	ta_H.clear();
	va_H.clear();
	eb_H.clear();
	tb_H.clear();
	vb_H.clear();
	a_H.clear();
	r_H.clear();
	w_H.clear();
	b_H.clear();
	usedDepots.clear();
	for (Map.Entry<XIndex, IloNumVar> varX : M.x.entrySet()) {
		if (M.model.getValue(varX.getValue()) >= 0.99) {
			x_H.add(varX.getKey());

			if (varX.getKey().staz.equals("fictius")) {
				e_H.put(varX.getKey(), M.model.getValue(M.e.get(varX.getKey())));
				t_H.put(varX.getKey(), M.model.getValue(M.t.get(varX.getKey())));
				v_H.put(varX.getKey(), M.model.getValue(M.v.get(varX.getKey())));

			} else {
				ea_H.put(varX.getKey(), M.model.getValue(M.ea.get(varX.getKey())));
				ta_H.put(varX.getKey(), M.model.getValue(M.ta.get(varX.getKey())));
				va_H.put(varX.getKey(), M.model.getValue(M.va.get(varX.getKey())));

				eb_H.put(varX.getKey(), M.model.getValue(M.eb.get(varX.getKey())));
				tb_H.put(varX.getKey(), M.model.getValue(M.tb.get(varX.getKey())));
				vb_H.put(varX.getKey(), M.model.getValue(M.vb.get(varX.getKey())));

				b_H.put(varX.getKey(), M.model.getValue(M.b.get(varX.getKey())));
			}
			a_H.put(varX.getKey().xj, M.model.getValue(M.a.get(varX.getKey().xj)));

			if (!varX.getKey().xj.contains("D")) {
				r_H.put(varX.getKey().xj, M.model.getValue(M.r.get(varX.getKey().xj)));
				w_H.put(varX.getKey().xj, M.model.getValue(M.w.get(varX.getKey().xj)));
			}

			if(varX.getKey().xi.contains("D") && !usedDepots.contains(varX.getKey().xi)) {
				usedDepots.add(varX.getKey().xi);
			}
		}
	}

	for (Map.Entry<XIndex, IloNumVar> varX : M.x.entrySet().stream().filter(e-> e.getKey().xi.contains("D")).collect(Collectors.toList())) {
		if(!usedDepots.contains(varX.getKey().xi))
			varX.getValue().setUB(0.0);
	}

    System.out.print(z_H);
	pr_out.print(z_H);
	boolean impr = false;
	if(z_H<z_best_H){
		z_best_H = z_H;
		CostoDrivers_H_best = CostoDrivers_H;
		CostoVeicoli_H_best = CostoVeicoli_H;
		CostoEnergia_H_best = CostoEnergia_H;
		x_best_H.clear();
		x_best_H = new ArrayList<>(x_H);
		System.out.println(";*");
		pr_out.println(";*");
		impr=true;
	}
	else {
		System.out.println();
		pr_out.println();
	}
	return  impr;
}
/*
public void set_TMIP() {
	TMIP=0;
	TMIP=(T_max)/(Math.min(NB_bar, NB)+1);
}
*/
public void generate_K_from_sol() throws UnknownObjectException, IloException {
	conta_k = 0;
	conta_not_k = 0;
	Kalt = new ArrayList<>(x_H);
	conta_k = Kalt.size();

	for (XIndex varX : M.x.keySet())
		if (!Kalt.contains(varX))
			not_Kalt.add(varX);

	conta_not_k = not_Kalt.size();

	for(XIndex vx: Kalt) // metto in P con valore 0 (e' intera nella soluzione corrente) le var del KS
	  P.put(vx, 0);

	LB = conta_k;
	generate_KDeg_arcs();
}
/*
public int compute_NB() {
	return (int) Math.ceil(((conta_not_k)/(double)LB));
}
*/
	/**
	 * Estrae gli archi (i,j) che non sono nel Kernel e li inserisce nel bucket da testare insieme a tutte le relative stazioni
	 * @param factor
	 * @return
	 */
public ArrayList<XIndex> getRandomBucket(double factor) {

	ArrayList<XIndex> bucket = new ArrayList<>();

	int countVariables = 0;
	ArrayList<XIndex> set = new ArrayList<>(not_Kalt);
    int varDaInserire = (int)Math.ceil(LB*factor);
	ArrayList<Archi> arcSet = new ArrayList<>(not_Kalt_arcs);

   if(varDaInserire>=set.size()) {
		System.out.println("WARNING++++++ tutte le variabili nel Bucket ++++++++++++++");
		varDaInserire=set.size();
		for(XIndex vx: not_Kalt)
			bucket.add(vx);
	}
    else {
		while (countVariables <= LB * factor && arcSet.size()>0 ) {

			double rval = kDegree.gen_ingoing.nextDouble();
			int i = (int) (rval * (arcSet.size() - 1));
			Archi selected = arcSet.get(i);
			for(XIndex xins: M.Xindici) {
			    if(xins.xi.equals(selected.idi) && xins.xj.equals(selected.idj)) {
					bucket.add(xins);
					countVariables++;
				}
			}
			arcSet.remove(i);
		}
	}
	return bucket;
}


public Map<XIndex, IloNumVar> cRandRemove(Map<XIndex, IloNumVar> currentSolution, int alpha, node[] N,List<Map.Entry<XIndex, IloNumVar>> entriesAndValuesToRemove) {
    // Ensure there are enough nodes in N0
    int availableNodes = Math.min(alpha, N.length);

    // Create a list of indices for N0
    List<Integer> indicesList = new ArrayList<>();
    for (int i = 0; i < N.length; i++) {
        indicesList.add(i);
    }

    // Shuffle the list of indices
    Random random = new Random();
    for (int i = indicesList.size() - 1; i > 0; i--) {
        int index = random.nextInt(i + 1);
        int temp = indicesList.get(i);
        indicesList.set(i, indicesList.get(index));
        indicesList.set(index, temp);
    }

    // Select alpha nodes randomly from N0
    node[] nodesToRemove = new node[availableNodes];
    for (int i = 0; i < availableNodes; i++) {
        nodesToRemove[i] = N[indicesList.get(i)];
    }

    // Print the selected nodes (optional)
    System.out.println("Nodes to be removed randomly:");
    for (node removedNode : nodesToRemove) {
        System.out.println("Node Removed " + removedNode);
        // Add more print statements as needed for other node properties
    }

    // Collect entries and values to be removed from the current solution

    for (node removedNode : nodesToRemove) {
        String nodeAsString = removedNode.toString();
        nodeAsString = "_" + nodeAsString + "_";
        Iterator<Map.Entry<XIndex, IloNumVar>> iterator = currentSolution.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<XIndex, IloNumVar> entry = iterator.next();
            // Check if the string representation of the node is contained in the entry's value
            if (entry.getValue().toString().contains(nodeAsString)) {
                entriesAndValuesToRemove.add(entry);
                iterator.remove(); // Remove the entry from the iterator to avoid ConcurrentModificationException
            }
        }
    }

    return currentSolution;
}

private List<String> findSmallestRoute(List<List<String>> routes) {
    List<String> smallestRoute = null;
    int smallestSize = Integer.MAX_VALUE;

    for (List<String> route : routes) {
        int currentSize = route.size();
        if (currentSize < smallestSize) {
            smallestSize = currentSize;
            smallestRoute = route;
        }
    }

    return smallestRoute;
}
public Map<XIndex, IloNumVar> cLessCustomers(Map<XIndex, IloNumVar> currentSolution, List<List<String>> resultRoutes) throws UnknownObjectException, IloException {
    List<String> smallestRoute = findSmallestRoute(resultRoutes);

    // Create a set for efficient lookup
    Set<String> smallestRouteSet = new HashSet<>(smallestRoute);

    // Create a copy of the original map to avoid concurrent modification exception
    Map<XIndex, IloNumVar> updatedSolution = new HashMap<>(currentSolution);

    for (Map.Entry<XIndex, IloNumVar> entry : updatedSolution.entrySet()) {
        XIndex key = entry.getKey();
        IloNumVar value = entry.getValue();

        // If the key is present in smallestRoute, remove the entry
        if (smallestRouteSet.contains(key.toString())) {
            currentSolution.remove(key);
        }
    }

    return currentSolution;
}


public Map<XIndex, IloNumVar> sRandRemove(Map<XIndex, IloNumVar> currentSolution, int alpha, node[] RS,List<Map.Entry<XIndex, IloNumVar>> entriesAndValuesToRemove) {
    // Ensure there are enough nodes in N0
	int stations_to_remove=alpha-5;
    int availableNodes = Math.min(stations_to_remove, RS.length);

    // Create a list of indices for N0
    List<Integer> indicesList = new ArrayList<>();
    for (int i = 0; i < RS.length; i++) {
        indicesList.add(i);
    }

    // Shuffle the list of indices
    Random random = new Random();
    for (int i = indicesList.size() - 1; i > 0; i--) {
        int index = random.nextInt(i + 1);
        int temp = indicesList.get(i);
        indicesList.set(i, indicesList.get(index));
        indicesList.set(index, temp);
    }

    // Select alpha nodes randomly from N0
    node[] nodesToRemove = new node[availableNodes];
    for (int i = 0; i < availableNodes; i++) {
        nodesToRemove[i] = RS[indicesList.get(i)];
    }

    // Print the selected nodes (optional)
    System.out.println("Stations to be removed randomly:");
    for (node removedNode : nodesToRemove) {
        System.out.println("Station Removed " + removedNode);
        // Add more print statements as needed for other node properties
    }

    // Collect entries and values to be removed from the current solution

    for (node removedNode : nodesToRemove) {
        String nodeAsString = removedNode.toString();
        nodeAsString = "_" + nodeAsString;
        Iterator<Map.Entry<XIndex, IloNumVar>> iterator = currentSolution.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<XIndex, IloNumVar> entry = iterator.next();
            // Check if the string representation of the node is contained in the entry's value
            if (entry.getValue().toString().contains(nodeAsString)) {
                entriesAndValuesToRemove.add(entry);
                iterator.remove(); // Remove the entry from the iterator to avoid ConcurrentModificationException
            }
        }
    }

    return currentSolution;
}


public Map<XIndex, IloNumVar> cLoadRemove(Map<XIndex, IloNumVar> currentSolution, int alpha, node[] N1HighestDemand,List<Map.Entry<XIndex, IloNumVar>> entriesAndValuesToRemove) {
    // Ensure there are enough nodes in N1HighestDemand
    int availableNodes = Math.min(alpha, N1HighestDemand.length);

    // Select the first alpha nodes based on highest demand
    node[] nodesToRemove = Arrays.copyOfRange(N1HighestDemand, 0, availableNodes);

    // Print the selected nodes (optional)
    System.out.println("Nodes to be removed based on highest demand:");
    for (node removedNode : nodesToRemove) {
        System.out.println("Node " + removedNode + ": Demand - " + removedNode.getDemand());
        // Add more print statements as needed for other node properties
    }

    // Collect entries and values to be removed from the current solution

    for (node removedNode : nodesToRemove) {
        String nodeAsString = removedNode.toString();
        nodeAsString = "_" + nodeAsString + "_";
        Iterator<Map.Entry<XIndex, IloNumVar>> iterator = currentSolution.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<XIndex, IloNumVar> entry = iterator.next();
            // Compare nodes based on their string representation
            if (entry.getValue().toString().contains(nodeAsString)) {
                entriesAndValuesToRemove.add(entry);
                iterator.remove(); // Remove the entry from the iterator to avoid ConcurrentModificationException
            }
        }
    }

    return currentSolution;
}


}
