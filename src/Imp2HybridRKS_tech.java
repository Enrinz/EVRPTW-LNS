import ilog.concert.*;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

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




//Destroy Code:
//public Map<XIndex, IloNumVar> cRandRemove(Map<XIndex, IloNumVar> currentSolution, int alpha, node[] N) {
//    // Ensure there are enough nodes in N0
//    int availableNodes = Math.min(alpha, N.length);
//
//    // Create a list of indices for N0
//    List<Integer> indicesList = new ArrayList<>();
//    for (int i = 0; i < N.length; i++) {
//        indicesList.add(i);
//    }
//
//    // Shuffle the list of indices
//    Random random = new Random();
//    for (int i = indicesList.size() - 1; i > 0; i--) {
//        int index = random.nextInt(i + 1);
//        int temp = indicesList.get(i);
//        indicesList.set(i, indicesList.get(index));
//        indicesList.set(index, temp);
//    }
//
//    // Select alpha nodes randomly from N0
//    node[] nodesToRemove = new node[availableNodes];
//    for (int i = 0; i < availableNodes; i++) {
//        nodesToRemove[i] = N[indicesList.get(i)];
//    }
//
//    // Print the selected nodes (optional)
//    System.out.println("Nodes to be removed randomly:");
//    for (node removedNode : nodesToRemove) {
//        System.out.println("Node Removed " + removedNode);
//        // Add more print statements as needed for other node properties
//    }
//
//    // Collect entries to be removed from the current solution
//    List<XIndex> entriesToRemove = new ArrayList<>();
//    for (node removedNode : nodesToRemove) {
//        for (Map.Entry<XIndex, IloNumVar> entry : currentSolution.entrySet()) {
//            // Check if the string representation of the node is contained in the entry's value
//            if (entry.getValue().toString().contains(removedNode.toString())) {
//                entriesToRemove.add(entry.getKey());
//                break; // Assuming XIndex is unique, no need to check further entries
//            }
//        }
//    }
//
//    // Remove the selected nodes from the current solution
//    for (XIndex entryToRemove : entriesToRemove) {
//        currentSolution.remove(entryToRemove);
//    }
//
//    System.out.println("REMOVED: " + entriesToRemove);
//
//    // Return the updated solution
//    return currentSolution;
//}

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

//    System.out.println("REMOVED: ");
//    for (Map.Entry<XIndex, IloNumVar> entryToRemove : entriesAndValuesToRemove) {
//        System.out.println("Key: " + entryToRemove.getKey() + ", Value: " + entryToRemove.getValue());
//    }

    // Return the updated solution
    return currentSolution;
}
//public Map<XIndex, IloNumVar> sRandRemove(Map<XIndex, IloNumVar> currentSolution, int alpha, node[] RS) {
//    // Ensure there are enough nodes in N0
//    int availableNodes = Math.min(alpha, RS.length);
//
//    // Create a list of indices for N0
//    List<Integer> indicesList = new ArrayList<>();
//    for (int i = 0; i < RS.length; i++) {
//        indicesList.add(i);
//    }
//
//    // Shuffle the list of indices
//    Random random = new Random();
//    for (int i = indicesList.size() - 1; i > 0; i--) {
//        int index = random.nextInt(i + 1);
//        int temp = indicesList.get(i);
//        indicesList.set(i, indicesList.get(index));
//        indicesList.set(index, temp);
//    }
//
//    // Select alpha nodes randomly from N0
//    node[] nodesToRemove = new node[availableNodes];
//    for (int i = 0; i < availableNodes; i++) {
//        nodesToRemove[i] = RS[indicesList.get(i)];
//    }
//
//    // Print the selected nodes (optional)
//    System.out.println("Stations to be removed randomly:");
//    for (node removedNode : nodesToRemove) {
//        System.out.println("Station Removed " + removedNode);
//        // Add more print statements as needed for other node properties
//    }
//
//    // Collect entries to be removed from the current solution
//    List<XIndex> entriesToRemove = new ArrayList<>();
//    for (node removedNode : nodesToRemove) {
//        for (Map.Entry<XIndex, IloNumVar> entry : currentSolution.entrySet()) {
//            // Check if the string representation of the node is contained in the entry's value
//            if (entry.getValue().toString().contains(removedNode.toString())) {
//                entriesToRemove.add(entry.getKey());
//                break; // Assuming XIndex is unique, no need to check further entries
//            }
//        }
//    }
//
//    // Remove the selected nodes from the current solution
//    for (XIndex entryToRemove : entriesToRemove) {
//        currentSolution.remove(entryToRemove);
//    }
//
//    System.out.println("REMOVED: " + entriesToRemove);
//
//    // Return the updated solution
//    return currentSolution;
//}
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

//    System.out.println("REMOVED: ");
//    for (Map.Entry<XIndex, IloNumVar> entryToRemove : entriesAndValuesToRemove) {
//        System.out.println("Key: " + entryToRemove.getKey() + ", Value: " + entryToRemove.getValue());
//    }

    // Return the updated solution
    return currentSolution;
}


// Define the Destroy function
//public Map<XIndex, IloNumVar> cRandRemove(Map<XIndex, IloNumVar> currentSolution, int alpha,node[] N0) {
//    int numNodesToRemove = alpha;
//    ArrayList<XIndex> nodesToRemove = getRandomNodes(currentSolution, numNodesToRemove);
//    //System.out.println("Soluzione pre Remove: " + currentSolution);
//
//    // Collect entries to be removed
//    List<XIndex> entriesToRemove = new ArrayList<>();
//    for (Map.Entry<XIndex, IloNumVar> entry : currentSolution.entrySet()) {
//        if (nodesToRemove.contains(entry.getKey())) {
//            entriesToRemove.add(entry.getKey());
//        }
//    }
//
//    // Remove the selected nodes from the current solution
//    for (XIndex entryToRemove : entriesToRemove) {
//        currentSolution.remove(entryToRemove);
//        
//    }
//    
//    System.out.println("REMOVED: "+entriesToRemove);
//    
//    //System.out.println("Soluzione post Remove: " + currentSolution);
//    return currentSolution;
//}
//
//// Helper function to get a random subset of nodes
//
//private ArrayList<XIndex> getRandomNodes(Map<XIndex, IloNumVar> currentSolution, int numNodes) {
// ArrayList<XIndex> allNodes = new ArrayList<>(currentSolution.keySet());
// ArrayList<XIndex> randomNodes = new ArrayList<>();
// Random random = new Random();
//
// // Ensure there are enough nodes to select
// int availableNodes = Math.min(numNodes, allNodes.size());
//
// // Randomly select nodes to remove
// for (int i = 0; i < availableNodes; i++) {
//     int randomIndex = random.nextInt(allNodes.size());
//     randomNodes.add(allNodes.get(randomIndex));
//     allNodes.remove(randomIndex);
// }
//
// return randomNodes;
//}

//
//public void cLoadRemove(Map<XIndex, IloNumVar> currentSolution, int alpha, node[] N1HighestDemand) {
//    // Ensure there are enough nodes to select
//    int availableNodes = Math.min(alpha, N1HighestDemand.length);
//
//    // Select the first alpha nodes based on highest demand
//    node[] nodesToRemove = Arrays.copyOfRange(N1HighestDemand, 0, availableNodes);
//
//    // Print the selected nodes (optional)
//    System.out.println("Nodes to be removed based on highest demand:");
//    for (node removedNode : nodesToRemove) {
//        System.out.println("Node " + removedNode + ": Demand - " + removedNode.getDemand());
//        // Add more print statements as needed for other node properties
//    }
//
//    // Collect entries to be removed from the current solution
//    List<String> idsToRemove = Arrays.stream(nodesToRemove)
//            .map(node::toString)
//            .collect(Collectors.toList());
//
//    // Remove the selected nodes from the current solution
//    List<XIndex> entriesToRemove = new ArrayList<>();
//    for (Map.Entry<XIndex, IloNumVar> entry : currentSolution.entrySet()) {
//        if (idsToRemove.contains(entry.getKey().toString())) {
//            entriesToRemove.add(entry.getKey());
//        }
//    }
//
//    // Remove the selected nodes from the current solution
//    for (XIndex entryToRemove : entriesToRemove) {
//        currentSolution.remove(entryToRemove);
//    }
//    System.out.println("REMOVED: "+entriesToRemove);
//
//    // Additional actions if needed
//    // ...
//}
//
//public Map<XIndex, IloNumVar> cLoadRemove(Map<XIndex, IloNumVar> currentSolution, int alpha, node[] N1HighestDemand) {
//    // Ensure there are enough nodes to select
//    int availableNodes = Math.min(alpha, N1HighestDemand.length);
//
//    // Select the first alpha nodes based on highest demand
//    node[] nodesToRemove = Arrays.copyOfRange(N1HighestDemand, 0, availableNodes);
//
//    // Print the selected nodes (optional)
//    System.out.println("Nodes to be removed based on highest demand:");
//    for (node removedNode : nodesToRemove) {
//        System.out.println("Node " + removedNode + ": Demand - " + removedNode.getDemand());
//        
//        // Add more print statements as needed for other node properties
//    }
//    // Collect entries to be removed
//    List<XIndex> entriesToRemove = new ArrayList<>();
//    for (Map.Entry<XIndex, IloNumVar> entry : currentSolution.entrySet()) {
//        if (nodesToRemove.contains(entry.getKey())) {
//            entriesToRemove.add(entry.getKey());
//        }
//    }
//
//    // Remove the selected nodes from the current solution
//    for (XIndex entryToRemove : entriesToRemove) {
//        currentSolution.remove(entryToRemove);
//        
//    }
//
//    System.out.println("REMOVED: "+nodesToRemove);
//
//    // Additional actions if needed
//    // ...
//
//    // Return the updated solution
//    return currentSolution;
//}

// Function to remove the first alpha elements from N1HighestDemand and update the solution
//public Map<XIndex, IloNumVar> cLoadRemove(Map<XIndex, IloNumVar> currentSolution, int alpha, node[] N1HighestDemand) {
//    // Ensure there are enough nodes in N1HighestDemand
//    int availableNodes = Math.min(alpha, N1HighestDemand.length);
//
//    // Select the first alpha nodes based on highest demand
//    node[] nodesToRemove = Arrays.copyOfRange(N1HighestDemand, 0, availableNodes);
//
//    // Print the selected nodes (optional)
//    System.out.println("Nodes to be removed based on highest demand:");
//    for (node removedNode : nodesToRemove) {
//        System.out.println("Node " + removedNode + ": Demand - " + removedNode.getDemand());
//        // Add more print statements as needed for other node properties
//    }
//
//    // Collect entries to be removed from the current solution
//    List<XIndex> entriesToRemove = new ArrayList<>();
//    for (node removedNode : nodesToRemove) {
//        for (Map.Entry<XIndex, IloNumVar> entry : currentSolution.entrySet()) {
//            // Compare nodes based on their string representation
//            if (entry.getValue().toString().contains(removedNode.toString())) {
//                entriesToRemove.add(entry.getKey());
//                break; // Assuming XIndex is unique, no need to check further entries
//            }
//        }
//    }
//
//    // Remove the selected nodes from the current solution
//    for (XIndex entryToRemove : entriesToRemove) {
//        currentSolution.remove(entryToRemove);
//    }
//
//    System.out.println("REMOVED: " + entriesToRemove);
//
//    // Additional actions if needed
//    // ...
//
//    // Return the updated solution
//    return currentSolution;
//}
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

//    System.out.println("REMOVED: ");
//    for (Map.Entry<XIndex, IloNumVar> entryToRemove : entriesAndValuesToRemove) {
//        System.out.println("Key: " + entryToRemove.getKey() + ", Value: " + entryToRemove.getValue());
//    }

    // Additional actions if needed
    // ...

    // Return the updated solution
    return currentSolution;
}

/*
public void write_ks_output(String args, IloCplex.Status stato) throws FileNotFoundException, IloException {
	String file_name = args.replace(".txt", "");
	pr_excel = new PrintStream(new File(file_name + "_excel.csv"));
	int NumeroSosteRS = 0;


	if (z_H < Double.POSITIVE_INFINITY) {
		double obj = z_H;
		for (int i = 1; i < M.N.length; i++)
			obj += M.FD * M.N[i].service_time;
		pr_excel.println("Obj;"+z_H+";Completo;"+obj+";Seed;"+kDegree.seed);
		pr_excel.println("vehicle;node;node;ebattery;time;speed;demand;dist;service time;ready;due;b;resbatt;arr time;w");
		String currNode = "D0";
		HashMap<String, HashMap<String, Boolean>> xmap = new HashMap<String, HashMap<String, Boolean>>();
		int xi = 0;

		int Node2Visit = M.N.length - 1;
		int numeroVeicolo = 0;

		ArrayList<XIndex> local_x_H = new ArrayList<XIndex>(x_best_H);
		// sino a che non ho visitato tutti i nodi customer
		while (Node2Visit >= 0) {

			String finalCurrNode = currNode;
			XIndex currX = local_x_H.stream().filter(el -> el.xi.equals(finalCurrNode)).findFirst().orElse(null);
			assert currX != null;
			xi = M.NodeSImap.get(currX.xi);
			local_x_H.remove(currX);

			// il succ e' customer
			if (currX.staz.equals("fictius")) {
				pr_excel.print(numeroVeicolo + ";" + currX.xi + ";" + currX.xj + ";" +
						e_H.get(currX) + ";" +
						t_H.get(currX) + ";" +
						v_H.get(currX) + ";" +
						M.N[xi].demand + ";" + M.D.get(currX.xi).get(currX.xj) + ";" + M.N[xi].service_time + ";" + M.N[xi].s + ";" + M.N[xi].e + ";0;"
				);
				if (!currX.xj.equals("D0"))
					pr_excel.println(r_H.get(currX.xj) + ";" + a_H.get(currX.xj) + ";" + w_H.get(currX.xj));
				else
					pr_excel.println("0;" + a_H.get(currX.xj) + ";0");

				currNode = currX.xj;

				Node2Visit--;
				if (Node2Visit > 0 && currNode.equals("D0")) {
					Node2Visit++;
					numeroVeicolo++;
				}
				// c'e' stazione tra i nodi
			} else {
				NumeroSosteRS++;
				pr_excel.print(numeroVeicolo + ";" + currX.xi + ";" + currX.staz + ";" +
						ea_H.get(currX) + ";" +
						ta_H.get(currX) + ";" +
						va_H.get(currX) + ";" +
						M.N[xi].demand + ";" + M.D.get(currX.xi).get(currX.staz) + ";" +
						M.N[xi].service_time + ";" + M.N[xi].s + ";" + M.N[xi].e + ";" + b_H.get(currX) + ";"
				);
				if (!currX.xj.equals("D0")) {
					if (!currX.xi.equals("D0"))
						pr_excel.println(M.gamma + ";" + (a_H.get(currX.xi) + M.N[xi].service_time + w_H.get(currX.xi) +
								ta_H.get(currX)) +
								";" + w_H.get(currX.xj));
					else
						pr_excel.println(M.gamma + ";" + ta_H.get(currX) +
								";" + w_H.get(currX.xj));
				} else
					pr_excel.println("0;" + a_H.get(currX.xj) + ";0");

				pr_excel.print(numeroVeicolo + ";" + currX.staz + ";" +
						currX.xj + ";" +
						eb_H.get(currX) + ";" +
						tb_H.get(currX) + ";" +
						vb_H.get(currX) + ";" +
						M.N[xi].demand + ";" +
						M.D.get(currX.staz).get(currX.xj) + ";0;0;0;0;");
				if (!currX.xj.equals("D0"))
					pr_excel.println(r_H.get(currX.xj) + ";" + a_H.get(currX.xj) + ";" + w_H.get(currX.xj));
				else
					pr_excel.println("0;" + a_H.get(currX.xj) + ";0");

				currNode = currX.xj;

				Node2Visit--;
				if (Node2Visit > 0 && currNode.equals("D0")) {
					Node2Visit++;
					numeroVeicolo++;
				}
			}
		}

		try(FileWriter fw = new FileWriter("RiassResults.csv", true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter outpr = new PrintWriter(bw)) {
			outpr.println(file_name + ";" + stato + ";" + z_H + ";" + (numeroVeicolo+1) + ";" + CostoVeicoli_H + ";" + CostoEnergia_H + ";" +
					CostoDrivers_H + ";" + timeSoFar + ";" + Gap_H);
			outpr.close();
		} catch (IOException e) {
			System.err.println("Errore scrittura file RiassResult.csv: "+e);
		}

	} else {
		pr_excel.println("No solution found");
	}
	pr_excel.close();

	PrintStream soste = new PrintStream(new FileOutputStream("Soste.csv", true));
	soste.println(nomeIstanza+";"+z_H+";"+NumeroSosteRS);
	soste.close();
}
*/
//
//public static void main(String[]args) throws IOException, IloException {
//
//	InstanceReaderGeneratorTech ir = new InstanceReaderGeneratorTech();
//	ir.generate(args);
//	Imp2HybridRKS_tech ks=new Imp2HybridRKS_tech();
//
//	if(args.length>=15)
//		if(args[14].equals("verb"))
//			ks.verboso = System.out;
//	//----------------
//	ks.file_model=args[1];
//	ks.T_max = Double.parseDouble(args[6]);
//	ks.limiteT = Integer.parseInt(args[11]);//tempo max iterazione
//
//	String name=args[1].replace(".txt", "");
//	nomeIstanza = name;
//	System.out.println(name);
//	ks.pr=new PrintStream(new File(name+"_"+args[15]+"_output_KS.txt"));
//	pr_out = new PrintStream(new File(name +"_"+args[15]+ "_out.csv")); // args[15] id run
//	System.out.println("Imp2 RKS");
//	pr_out.println(" Imp2 RKS");
//
//	ks.kDegree=new matheuristic_k_degree_hybridOneGenerator_tech(args); // invoca la cloneless_tech2 !!!!!!!!!!!!!!!!!
//	ks.kDegree.M.verbCloneless = ks.verboso;
//	ks.kDegree.M.TempoCPU = System.currentTimeMillis();
//	ks.kDegree.M.num_veic=Integer.parseInt(args[5]);
//    int originalNumVeic = ks.kDegree.M.num_veic;
//	ks.kDegree.M.timeLimit = Double.parseDouble(args[10]);
//
//	ks.kDegree.M.R = new	RechargeTypes[ir.Inst.NumTechs];
//	for (int tec = 0; tec<ir.Inst.NumTechs;tec++){
//		ks.kDegree.M.R[tec] = new RechargeTypes();
//		ks.kDegree.M.R[tec].id=Integer.parseInt(ir.Inst.TechId[tec]);
//		ks.kDegree.M.R[tec].description="Tec_"+tec;
//		ks.kDegree.M.R[tec].cost = ir.Inst.EnCost[tec];
//		ks.kDegree.M.R[tec].speed = ir.Inst.RecSpeed[tec];
//
//		if(ir.Inst.RecSpeed[tec] < ks.kDegree.M.speed_min)
//			ks.kDegree.M.speed_min = ir.Inst.RecSpeed[tec];
//	}
//
//	for(Map.Entry<String,ArrayList<String>> StaTec: ir.Inst.RSTech.entrySet())
//	{
//		String stat = StaTec.getKey();
//		ArrayList<RechargeTypes> Rlist = new ArrayList<>();
//		for (int tec=0;tec<StaTec.getValue().size();tec++) {
//			String tecId = StaTec.getValue().get(tec);
//			RechargeTypes Relem = new RechargeTypes();
//			Relem.id=Integer.parseInt(StaTec.getValue().get(tec));
//			for (int tt=0;tt<ir.Inst.TechId.length;tt++) {
//				if (ir.Inst.TechId[tt].equals(tecId)) {
//					Relem.speed = ir.Inst.RecSpeed[tt];
//					Relem.cost = ir.Inst.EnCost[tt];
//					Relem.description = "Tec_" + tt;
//					Rlist.add(Relem);
//				}
//			}
//		}
//		ks.kDegree.M.RRT.put(stat,Rlist);
//	}
//
//	//parte della k-degree
//	int trueNumVeic = ks.kDegree.M.num_veic;
////	ks.kDegree.M.num_veic = 1; // solo il dep D0
//	ks.kDegree.M.generate_nodes(ir);
//	ks.kDegree.M.generate_distances();
//	ks.kDegree.M.init_parameters(ir);
//	ks.kDegree.M.compute_S();
//	ks.kDegree.M.initVariables_Ok();
//	//----------------------------------------------------------
//	// considera un solo deposito e non mette limite al numero di veicoli
//	// annullo variabili con soste a stazioni e che partono o arrivano da dep diversi da D0
//	/*for(XIndex xind: ks.kDegree.M.Xindici){
//		if(!xind.staz.equals("fictius")) {
//			ks.kDegree.M.x.get(xind).setUB(0.0);
//			ks.kDegree.M.ZeroedXvar.add(xind); // tutte le var con stazioni sono inizialmente fissate a zero
//		}
//		else if((xind.xi.startsWith(("D")) && !xind.xi.equals("D0")) || (xind.xj.startsWith(("D")) && !xind.xj.equals("D0")))
//		{
//			ks.kDegree.M.x.get(xind).setUB(0.0);
//			ks.kDegree.M.ZeroedXvar.add(xind);
//		}
//	}
//*/
//	//----------------------------------------------------------
//	//ks.kDegree.M.initModelOneVeic(ks.kDegree.M,trueNumVeic); // considera un solo deposito, non mette limite al numero di veicoli e non considera i consumi energetici
//	ks.kDegree.M.initModel(ks.kDegree.M);
//	ks.kDegree.feasible_arcs();
//
//	ks.finalTimelimit = Integer.parseInt(args[12]); // tempo massimo iterazione finale
//	ks.limiteTkDeg = Integer.parseInt(args[10]); // tempo massimo kDegree
//	cloneless_tech.timeLimit = ks.limiteTkDeg;
//	cloneless_tech.verbCloneless = ks.verboso;
//
//	for(String st:ks.kDegree.degree_in.keySet()) {
//		ks.kDegree.degree_ingoing.put(st, ks.kDegree.degree_in.get(st));
//	}
//	for(String st:ks.kDegree.degree_out.keySet()) {
//		ks.kDegree.degree_outgoing.put(st, ks.kDegree.degree_out.get(st));
//	}
//
//	ks.kDegree.compute_start_ingoing_degree();
//	ks.kDegree.compute_start_outgoing_degree();
//
//	ks.kDegree.probabilities();
//	ks.kDegree.increasing_degree_ingoing();
//	ks.kDegree.increasing_degree_outgoing();
//	ks.kDegree.copy();
//
//	System.err.println("k-degree starts");
//	GlobalStartTime = System.currentTimeMillis();
//	int usedVehicles = 0;
//	double time_degree=0;
//	double bestSoFar = Double.POSITIVE_INFINITY;
//	int notImprIter = 0 ;
//	while(!(ks.kDegree.M.model.getStatus().equals(IloCplex.Status.Optimal)||ks.kDegree.M.model.getStatus().equals(IloCplex.Status.Feasible))
//	      || (time_degree < ks.limiteTkDeg )){
//
//		ks.kDegree.random_extract();
//		/*System.err.println("archi ingoing");
//		for(String st:ks.kDegree.closest_ingoing.keySet())
//			for(String st1:ks.kDegree.closest_ingoing.get(st)) {
//				System.err.println(st+"-->"+st1);
//			}
//		System.err.println("archi outgoing");
//		for(String st:ks.kDegree.closest_outgoing.keySet())
//			for(String st1:ks.kDegree.closest_outgoing.get(st)) {
//				System.err.println(st1+"-->"+st);
//			}*/
//		ks.kDegree.set_zero_variables_outgoing();
//
//		// annullo variabili con stazioni
//		//ks.kDegree.AzzeraVarX();
//       // ks.kDegree.M.model.exportModel("mod_kdeg.lp");
//		//System.out.println("KDeg K': "+ks.kDegree.k_prime);
//		if(ks.kDegree.M.solve(name+"_output_k_degree_matheuristic"+ks.kDegree.k_prime+".txt")) {
//
//			if (ks.kDegree.M.model.getStatus().equals(IloCplex.Status.Infeasible)) {
//				ks.kDegree.restore_zero_variables_outgoing();
//				ks.kDegree.reset();
//				ks.kDegree.copy_opposite();
//			}
//			else
//			{
//				double Obj = ks.kDegree.M.model.getObjValue();
//				if(Obj < bestSoFar) {
//					bestSoFar = Obj;
//					usedVehicles = ks.kDegree.M.storeSol();
//					System.out.println("KDeg: "+bestSoFar);
//				}
//				else{
//					if(notImprIter>1){
//						ks.kDegree.k_prime += 2;
//						notImprIter=0;
//					}
//					else{
//						notImprIter++;
//						//System.out.println("KDeg Not Impr "+notImprIter);
//					}
//				}
//			}
//		}
//
//		time_degree = (System.currentTimeMillis() - GlobalStartTime)/1000.0;
//		if (time_degree >= ks.T_max)
//			break;
//		if(time_degree < ks.limiteTkDeg ) {
//			ks.kDegree.restore_zero_variables_outgoing();
//			ks.kDegree.reset();
//			ks.kDegree.copy_opposite();
//		}
//	}
//	if(time_degree >=ks.T_max)
//		return;
//
//	System.err.println("A solution "+ks.kDegree.M.model.getStatus()+" was found with objective "+bestSoFar+ " in "+(time_degree));
//
//	ks.generate_KDeg_arcs_xinit();
//	//usedVehicles = ks.kDegree.M.storeSol();
//
//	//fine parte k-Degree
//	ks.M=ks.kDegree.M;
//
//	prRes = new PrintStream(new FileOutputStream("Risultati.csv", true));
//
//	if (usedVehicles > 1) // se uso pi� di un veicolo devo aggiungere le copie dei depositi
//	{
//		trueNumVeic = usedVehicles;
//		ks.M.num_veic = trueNumVeic;
//
//		System.out.println("usati " + trueNumVeic);
//		ks.M.model = new IloCplex(); // rigenero il modello cplex
//
//		ks.M.add_new_deps(trueNumVeic - 1); // aggiungo a N0 e Dep le copie dei depositi
//
//		ks.M.generate_distances(); // rigenero le distanze
//		ks.M.add_velLimits(ir);
//		ks.M.compute_S();
//		ks.M.initVariables_Ok();
//		ks.M.initModel(ks.M); // ricostruisco tutto il modello
//	}
//	else {
//		// ripristino variabili con soste a stazioni e partenze da D0
//		for (XIndex xind : ks.M.ZeroedXvar) {
//			ks.M.x.get(xind).setUB(1.0);
//		}
//		// aggiungo i vincoli mancanti
//		ks.M.add_constraint_4_();
//		ks.M.add_constraint_17_();
//		ks.M.add_constraint_18_();
//		//System.out.println("18 - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//		ks.M.add_constraint_19_();
//		//System.out.println("19 - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//		ks.M.add_constraint_20_();
//		//System.out.println("20 - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//		ks.M.add_constraint_21_();
//		//System.out.println("21 - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//		ks.M.add_constraint_22_();
//		//System.out.println("22 - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//		ks.M.add_constraint_23a_();
//		//System.out.println("23a - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//		ks.M.add_constraint_23b_();
//		//System.out.println("23b - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//		//commentiamo i vincoli 24 perch� doppiano i 26
//		//add_constraint_24_();
//		//System.out.println("24 - " + (System.currentTimeMillis() - ks.M.TempoCPU)/1000.0);
//		ks.M.add_constraint_25ab_();
//		//System.out.println("25ab - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//		//add_constraint_25b_();
//		//System.out.println("25b - " + (System.currentTimeMillis() - ks.M.TempoCPU)/1000.0);
//		ks.M.add_constraint_26_();
//		//System.out.println("26 - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//		ks.M.add_constraint_27_();
//		//System.out.println("27 - " + (System.currentTimeMillis() - ks.M.TempoCPU) / 1000.0);
//	}
//
//	System.out.println("Warm");
//	ArrayList<IloRange> warmStartConstr = new ArrayList<>();
//	for(XIndex vx: ks.M.x_init)
//	{
//		// Vincoli "warmstart"
//		//impongo che la sommatoria di x_ijs (incluso l'arco) sia 1 per gli archi (i,j) nella soluzione iniziale
//		// e se l'arco � (D0, j) o (i, D0) metto nella somma tutte le variabili (D*,j) o (i,D*)
//		IloLinearNumExpr exp= ks.M.model.linearNumExpr();
//		if(vx.xi.equals(("D0")))
//		{
//			for(Map.Entry<XIndex,IloNumVar> varX: ks.M.x.entrySet())
//				if(varX.getKey().xi.startsWith("D")  && varX.getKey().xj.equals(vx.xj) ) {
//					exp.addTerm(1, varX.getValue());
//				}
//		}
//		if( vx.xj.equals("D0"))
//		{
//			for(Map.Entry<XIndex,IloNumVar> varX: ks.M.x.entrySet())
//				if(varX.getKey().xj.startsWith("D")  && varX.getKey().xi.equals(vx.xi) ) {
//					exp.addTerm(1, varX.getValue());
//				}
//		}
//		if(!vx.xi.equals("D0") && !vx.xj.equals("D0"))  {
//			for(Map.Entry<XIndex,IloNumVar> varX: ks.M.x.entrySet())
//				if(varX.getKey().xi.equals(vx.xi) && varX.getKey().xj.equals(vx.xj)){
//					exp.addTerm(1, varX.getValue());
//				}
//		}
//
//		warmStartConstr.add( ks.M.model.addEq(exp, 1, "Constr_warmstart_"+vx.xi+"_"+vx.xj));
//	}
//
//	//ks.M.model.exportModel("mod_ws.lp");
//	ks.M.solve(""); // risolvo il modello imponendo il routing "esteso" della soluzione iniziale
//	usedVehicles = ks.M.storeSol();
//
//	// rimuovo il vincolo di warmstart
//	for (IloRange constrWS: warmStartConstr) {
//		ks.M.model.delete(constrWS);
//	}
//
//	// creo il warm start vero per cplex
//	ks.M.setWarmStart(usedVehicles);
//
//	System.out.println("RKS Starts");
//	IloCplex.Status stato= ks.RandKernSearchKDegree();
//
//	stato=ks.FinalIteration(args[15]);
//
//	prRes.close();
//	pr_out.close();
//
//	return;
//}
//
}
