import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * Questa classe implementa il modello proposto nel paper JCP del 2019
 * "Development of energy consumption optimization model for the
electric vehicle routing problem with time windows"
 * @author Ornella Pisacane
 *
 */


public class cloneless_tech2 {

	public ArrayList<XIndex> ZeroedXvar = new ArrayList<XIndex>();
	long TempoCPU = 0;
	public static PrintStream prMod=null;
	public  PrintStream verbCloneless = System.out;
	int num_veic;
	node[] N, N0;//set di clienti
	HashMap<String, Integer>NodeSImap = new HashMap<String, Integer>();
	HashMap<String, Integer>StationMap = new HashMap<String, Integer>();
	HashMap<String, node>NodesMap = new HashMap<String, node>();
	ArrayList<String> StationIDs = new ArrayList<String>();
	node[] RS;// set delle stazioni
	node[] N1;//set di tutti i nodi
	HashMap<String, HashMap<String, Double>> D;//distanze tra coppie di nodi i,j
	HashMap<String, HashMap<String, Double>> surrogate_D;//distanze surrogate tra coppie di nodi i,j
	HashMap<IJind, Double>v_min,v_max;//v_min e v_max sono le velocit� minime e massime fra ogni coppia di nodi
	HashMap<String, HashMap<String, ArrayList<String>>> S_ij=new HashMap<String, HashMap<String, ArrayList<String>>>();
	ArrayList<XIndex> Xindici = new ArrayList<XIndex>();
	//ArrayList<XIndex> Xindici_init = new ArrayList<XIndex>();
	ArrayList<XIndex> Xindici_add = new ArrayList<XIndex>();
	ArrayList<IJind> IJindici = new ArrayList<IJind>();
	//ArrayList<IJind> IJindici_init = new ArrayList<IJind>();
	//ArrayList<IJind> IJindici_add = new ArrayList<IJind>();
	ArrayList<IJind> Findici = new ArrayList<IJind>();
	//ArrayList<IJind> Findici_init = new ArrayList<IJind>();
	//ArrayList<IJind> Findici_add = new ArrayList<IJind>();

	HashMap<XIndex, IloConversion>XConv;
	//la minima e la massima velocit� consentita sull'arco i e j
	double epsilon, Cl,Cb,FC,FE,FD, phi,sigma, M, gamma;//rispetto al paper F � stato sotituito con FC // rho unused
	int[] P, Q;
	double[] kp, bp, kq, bq;
	int timeLimitMIPiter=0;

	public static int trueNumVeic = 0;
//nuovi parametri
	node[] Dep;//set dei depositi
	ArrayList<String> ListOfDeps = new ArrayList<>();
	RechargeTypes[] R;//diverse tecnologie di ricarica
	double speed_min=Double.POSITIVE_INFINITY; //velocit� di ricarica minima
	HashMap<String,ArrayList<RechargeTypes>> RRT=new HashMap<String,ArrayList<RechargeTypes>>();//sotto-insieme di tecnologie di ricarica disponibili ad una stazione
	ArrayList<IJSRIndex> IJSRindici = new ArrayList<IJSRIndex>();
	//ArrayList<IJSRIndex> IJSRindici_init = new ArrayList<IJSRIndex>();
	//ArrayList<IJSRIndex> IJSRindici_add = new ArrayList<IJSRIndex>();
	
//variabili decisionali
HashMap<XIndex, IloNumVar>x=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>b=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>c=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>t=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>v=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>e=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>ta=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>va=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>ea=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>tb=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>vb=new HashMap<XIndex,IloNumVar>();
HashMap<XIndex, IloNumVar>eb=new HashMap<XIndex,IloNumVar>();
HashMap<IJind, IloNumVar>f=new HashMap<IJind,IloNumVar>();
HashMap<String, IloNumVar>r=new HashMap<String, IloNumVar>();
HashMap<String, IloNumVar>dr=new HashMap<String, IloNumVar>(); // deviazioni rispetto r >= sigma
HashMap<String, IloNumVar>w=new HashMap<String, IloNumVar>();
HashMap<String, IloNumVar>a=new HashMap<String, IloNumVar>();
HashMap<String, IloNumVar>viola=new HashMap<String, IloNumVar>();

//nuove variabili decisionali
HashMap<IJSRIndex, IloNumVar>beta=new HashMap<IJSRIndex,IloNumVar>();
HashMap<String, IloNumVar>r_v_0=new HashMap<String, IloNumVar>();


IloRange newVinc, newVinc2, defCostoEn, defCostoDrivers, defCostoVei, constr4bis;
IloRange[] Constr2Range, Constr3Range, Constr31Range;
IloLinearNumExpr ob;
IloObjective obiett;

IloNumVar violK; // usato nei vincoli 4bis

// funzione obj
	IloNumVar CostoVeicoli;
	IloNumVar CostoDrivers;
	IloNumVar CostoEnergia;
	IloNumVar CostoEnergiaInitSol; // usato per la soluzione iniziale
	IloNumVar CostoDriversInitSol; // usato per la soluzione iniziale
	IloNumVar CostoVeicoliInitSol; // usato per la soluzione iniziale

HashMap<String, HashMap<String, String[]>>nameX, nameB, nameC;
HashMap<String, HashMap<String, String>> nameT, nameV, nameE;

IloCplex model;//object model
//output Excel
public static PrintStream pr_excel=null;
//output stream
public static PrintStream pr=null;
public static PrintStream outpr=null;
//output Sij
public static PrintStream pr_sij=null;
//parametri per la scrittura su file e per il setting del CPU time
String name_file;//name of the input file
File file;
double Cplex_time_spent,  total_cplex_time=0;
public static double timeLimit = 3600.0;
int conta_vincoli=0, conta_variabili_int, conta_variabili_cont=0;

public ArrayList<XIndex>x_init=new ArrayList<XIndex>();

public void compute_surrogate_distances() {
	surrogate_D=new HashMap<String, HashMap<String, Double>>();
	for(int i=0;i<N.length;i++) {
		for(int j=0;j<N.length;j++) {

			if(N[i].id.equals(N[j].id))
				continue;

			IJind ij=new IJind(N[i].id, N[j].id);
			double T_tilde=D.get(N[i].id).get(N[j].id)/v_min.get(ij);
			double wait=Math.max(0, N[j].s-N[i].e-T_tilde);
			if(surrogate_D.containsKey(N[i].id)) {
				surrogate_D.get(N[i].id).put(N[j].id, FE*D.get(N[i].id).get(N[j].id)+FD*wait);
			}
			else {
				HashMap<String, Double> aus=new HashMap<String, Double>();
				aus.put(N[j].id, FE*D.get(N[i].id).get(N[j].id)+FD*wait);
				surrogate_D.put(N[i].id, aus);
			}
		}
	}
}

public boolean feasible_demands(node i, node j) {
	if(i.id.contains("D"))
		if(j.demand>Cl) 
			{
			return false;
			}
	if(j.id.contains("D"))
		if(i.demand>Cl) 
			{
			return false;
			}
	if(i.demand+j.demand>Cl) {
		return false;
	}
	return true;
}

public boolean feasible_time_windows(node i, node j) {
	if(i.s+i.service_time+D.get(i.id).get(j.id)/v_max.get(new IJind(i.id,j.id))>j.e) {
		return false;
	}
return true;
}

public boolean feasible_return_depot(node i, node j) {
	if(i.id.contains("D")) {
		if(D.get(i.id).get(j.id)/v_max.get(new IJind(i.id,j.id))+D.get(j.id).get(i.id)/v_max.get(new IJind(j.id,i.id))>Dep[0].e) {
			return false;
		}
	}
	else {
		if(j.id.contains("D")) {
			if(D.get(i.id).get(j.id)/v_max.get(new IJind(i.id,j.id))>Dep[0].e) {
				return false;
			}
		}
			else {
				if(D.get(Dep[0].id).get(i.id)/v_max.get(new IJind(Dep[0].id,i.id))+D.get(i.id).get(j.id)/v_max.get(new IJind(i.id,j.id))+D.get(j.id).get(Dep[0].id)/v_max.get(new IJind(j.id,Dep[0].id))>Dep[0].e) {
					return false;
				}
			}
	}
return true;
}

//Algoritmo 1
//e^{min}_{is}=K_q V^{min}_{is}+I_q+\Phi Q_j
//e^{min}_{sj}=K_q V^{min}_{sj}+I_q+\Phi Q_j
public void compute_S() throws FileNotFoundException {
	for(int i=0;i<N1.length;i++) {
		for (int j = 0; j < N1.length; j++) {
			if(N1[i].id.contains("D") && N1[j].id.contains("D")) continue;
			if (i != j && !N1[i].id.contains("S") && !N1[j].id.contains("S")&&feasible_demands(N1[i], N1[j]) && feasible_return_depot(N1[i], N1[j]) && feasible_time_windows(N1[i], N1[j])) {
				int s_star = -1;
				double minimum = Double.POSITIVE_INFINITY;
				if (RS.length > 0) {
					for (int s = 0; s < RS.length; s++) {
						if ((D.get(N1[i].id).get(RS[s].id) == 0) || (D.get(N1[j].id).get(RS[s].id) == 0))
							continue;
						for (int q = 0; q < Q.length; q++) {
							if ((D.get(N1[i].id).get(RS[s].id) * (kq[q] * v_min.get(new IJind(N1[i].id, RS[s].id)) + bq[q] + phi * N1[j].demand) <= (gamma - sigma))
									&&
									(D.get(RS[s].id).get(N1[j].id) * (kq[q] * v_min.get(new IJind(RS[s].id, N1[j].id)) + bq[q] + phi * N1[j].demand) <= (gamma - sigma))) {
								if (D.get(N1[i].id).get(RS[s].id) + D.get(RS[s].id).get(N1[j].id) < minimum) {
									minimum = D.get(N1[i].id).get(RS[s].id) + D.get(RS[s].id).get(N1[j].id);
									s_star = s;
								}
							}
						}
					}
					
					ArrayList<String> s_better = new ArrayList<String>();
					if (s_star > -1) {
						s_better.add(RS[s_star].id);
						if (S_ij.containsKey(N1[i].id))
							S_ij.get(N1[i].id).put(N1[j].id, s_better);
						else {
							HashMap<String, ArrayList<String>> S_ijs = new HashMap<String, ArrayList<String>>();
							S_ijs.put(N1[j].id, s_better);
							S_ij.put(N1[i].id, S_ijs);
						}
						for (int s = 0; s < RS.length && s != s_star; s++) {
							for (int q = 0; q < Q.length; q++) {
								if ((D.get(N1[i].id).get(RS[s].id) == 0) || (D.get(N1[j].id).get(RS[s].id) == 0))
									continue;
								if (((D.get(N1[i].id).get(RS[s].id) >= D.get(N1[i].id).get(RS[s_star].id)) &&
										(D.get(RS[s].id).get(N1[j].id) >= D.get(RS[s_star].id).get(N1[j].id)))
										||
										(D.get(N1[i].id).get(RS[s].id) * (kq[q] * v_min.get(new IJind(N1[i].id, RS[s].id)) + bq[q] + phi * N1[j].demand) > (gamma - sigma))
										||
										(D.get(RS[s].id).get(N1[j].id) * (kq[q] * v_min.get(new IJind(RS[s].id, N1[j].id)) + bq[q] + phi * N1[j].demand) > (gamma - sigma))) {
									continue;
								} else {
									S_ij.get(N1[i].id).get(N1[j].id).add(RS[s].id);

								}
							}
						}
					}
				}

				if (S_ij.containsKey(N1[i].id)) {
					if (feasible_time_windows(N1[i], N1[j]) && feasible_demands(N1[i], N1[j]) && feasible_return_depot(N1[i], N1[j])) {

						if (S_ij.get(N1[i].id).containsKey(N1[j].id))
							S_ij.get(N1[i].id).get(N1[j].id).add("fictius");
						else {
							HashMap<String, ArrayList<String>> S_ijs = new HashMap<String, ArrayList<String>>();
							ArrayList<String> s_fict = new ArrayList<String>();
							s_fict.add(("fictius"));
							S_ij.get(N1[i].id).put(N1[j].id, s_fict);
						}
					}
				} else {
					if (feasible_time_windows(N1[i], N1[j]) && feasible_demands(N1[i], N1[j]) && feasible_return_depot(N1[i], N1[j])) {

						HashMap<String, ArrayList<String>> S_ijs = new HashMap<String, ArrayList<String>>();
						ArrayList<String> s_fict = new ArrayList<String>();
						s_fict.add(("fictius"));
						S_ijs.put(N1[j].id, s_fict);
						S_ij.put(N1[i].id, S_ijs);
					}
				}
			}
		}
	}
	Xindici.clear();
	//Xindici_init.clear();
	Xindici_add.clear();
	IJindici.clear();
	//IJindici_init.clear();
	//IJindici_add.clear();
	Findici.clear();
	//Findici_init.clear();
	//Findici_add.clear();
	IJSRindici.clear();
	//IJSRindici_init.clear();
	//IJSRindici_add.clear();
	for(String id:S_ij.keySet()) {
		for (String id_1 : S_ij.get(id).keySet()) {
		for (String id_2 : S_ij.get(id).get(id_1)) {
				Xindici.add(new XIndex(id, id_1, id_2));
				if((id.startsWith("D") && !id.equals("D0"))||(id_1.startsWith("D") && !id_1.equals("D0")) ){
					Xindici_add.add(new XIndex(id, id_1, id_2));
				}
				//if(!((id.startsWith("D") && !id.equals("D0")) || (id_1.startsWith("D") && !id_1.equals("D0"))) ){
				//	Xindici_init.add(new XIndex(id, id_1, id_2));
				//}

				if (!IJindici.contains(new IJind(id, id_1))) {
					IJindici.add(new IJind(id, id_1));
					Findici.add(new IJind(id, id_1));
					//if(!((id.startsWith("D") && !id.equals("D0")) || (id_1.startsWith("D") && !id_1.equals("D0"))) ) {
					//	IJindici_init.add(new IJind(id, id_1));
					//	Findici_init.add(new IJind(id, id_1));
					//}
					//if(((id.startsWith("D") && !id.equals("D0")) || (id_1.startsWith("D") && !id_1.equals("D0"))) ) {
					//	IJindici_add.add(new IJind(id, id_1));
					//	Findici_add.add(new IJind(id, id_1));
					//}
				}
				if (!id_2.equals("fictius") && !IJindici.contains(new IJind(id, id_2))){
					IJindici.add(new IJind(id, id_2));
					//if(!(id.startsWith("D") && !id.equals("D0")))
					//	IJindici_init.add(new IJind(id, id_2));
					//if((id.startsWith("D") && !id.equals("D0")))
					//	IJindici_add.add(new IJind(id, id_2));
				}

				if (!id_2.equals("fictius") && !IJindici.contains(new IJind(id_2, id_1))){
					IJindici.add(new IJind(id_2, id_1));
					//if(!(id_1.startsWith("D") && !id_1.equals("D0")))
					//	IJindici_init.add(new IJind(id_2, id_1));
					//if((id_1.startsWith("D") && !id_1.equals("D0")))
					//	IJindici_add.add(new IJind(id_2, id_1));
				}
			}
		}
	}
	for(String id:S_ij.keySet()) {
		for (String id_1 : S_ij.get(id).keySet()) {
			//System.err.println(id + "\t" + id_1 + "\t" + S_ij.get(id).get(id_1));
			for (String id_2 : S_ij.get(id).get(id_1)) {
					if(!id_2.contains("fictius")) {
					for(RechargeTypes rt:RRT.get(id_2)) {
						IJSRIndex ijrsind=new IJSRIndex(id, id_1, id_2, rt.id);
						IJSRindici.add(ijrsind);
						//if(!((id.startsWith("D") && !id.equals("D0")) || (id_1.startsWith("D") && !id_1.equals("D0"))) )
						//	IJSRindici_init.add(ijrsind);
						//if(((id.startsWith("D") && !id.equals("D0")) || (id_1.startsWith("D") && !id_1.equals("D0"))) )
						//	IJSRindici_add.add(ijrsind);
					}
				}
			}
		}
	}
	
	return;
}

public void init(String nf) throws IOException {
	name_file=nf.replace(".txt", "");
	file = new File("output_"+nf);
}
public void add_constraints() throws IloException {

	//  update constraints 2 ----------------------------------------------------
	for(int j=0;j<N.length;j++) {
		IloLinearNumExpr exp = model.linearNumExpr();
		String nodoj = N[j].id;
		for (XIndex xind : Xindici.stream().filter(e -> e.xj.equals(nodoj) && e.xi.contains("D") && !e.xi.equals("D0")).collect(Collectors.toList())) {
			exp.addTerm(1, x.get(xind));
		}
		model.addToExpr(Constr2Range[j],exp );
	}

	//  update constraints 3 ----------------------------------------------------
	for(int i=0;i<N.length;i++) {
		IloLinearNumExpr exp= model.linearNumExpr();
		String nodoi = N[i].id;
		for (XIndex xind:Xindici.stream().filter(e-> e.xi.equals(nodoi) && e.xj.contains("D") && !e.xj.equals("D0")).collect(Collectors.toList()))
		{
			exp.addTerm(1,x.get(xind));
		}
		model.addToExpr(Constr3Range[i],exp );
	}

	model.delete(constr4bis); // elimino constraint 4bis

	add_constraint_4_();

	//add constraints 5 ----------------------------------------------------
	for (int i=1;i<Dep.length;i++) {
		IloLinearNumExpr exp= model.linearNumExpr();
		String dep = Dep[i].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep)).collect(Collectors.toList()))
		{
			exp.addTerm(1,x.get(xind));
		}
		for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep)).collect(Collectors.toList()))
		{
			exp.addTerm(-1,x.get(xind));
		}
		model.addEq(exp, 0, "Constr_(5)_for_depot_"+Dep[i].id);
		conta_vincoli++;
	}

	// add constr (6) ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		for (int p = 0; p < P.length; p++) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, t.get(xind));
			exp.addTerm(-60 * D.get(xind.xi).get(xind.xj) * kp[p], v.get(xind));
			double bigM = 60 * D.get(xind.xi).get(xind.xj) * (Math.max(kp[0]*v_min.get(new IJind(xind.xi,xind.xj)),0))+
					60 * D.get(xind.xi).get(xind.xj)*bp[0];
			exp.addTerm(-bigM, x.get(xind));

			model.addGe(exp, 60 * D.get(xind.xi).get(xind.xj) * bp[p]-bigM,"Constr_(6)_for_pair_" + xind.xi + "_" + xind.xj+"_"+p);
			conta_vincoli++;
		}
	}

	// add constr 7 ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		for (int p = 0; p < P.length; p++) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, ta.get(xind));
			exp.addTerm(-60 * D.get(xind.xi).get(xind.staz) * kp[p], va.get(xind));
			double bigM = 60 * D.get(xind.xi).get(xind.staz) * (Math.max(kp[0]*v_min.get(new IJind(xind.xi,xind.staz)),0))+
					60 * D.get(xind.xi).get(xind.staz)*bp[0];
			exp.addTerm(-bigM, x.get(xind));

			model.addGe(exp, 60 * D.get(xind.xi).get(xind.staz) * bp[p] -bigM,
					"Constr_(7)_for_pairA_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz + "_" + p);
			conta_vincoli++;
		}
	}

	// add constr 8 ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		for (int p = 0; p < P.length; p++) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp = model.linearNumExpr();
			exp.addTerm(1, tb.get(xind));
			exp.addTerm(-60 * D.get(xind.staz).get(xind.xj) * kp[p], vb.get(xind));
			double bigM = 60 * D.get(xind.staz).get(xind.xj) * (Math.max(kp[0]*v_min.get(new IJind(xind.staz,xind.xj)),0))+
					60 * D.get(xind.staz).get(xind.xj)*bp[0];

			exp.addTerm(-bigM, x.get(xind));

			model.addGe(exp,60 * D.get(xind.staz).get(xind.xj) * bp[p]	-bigM,
					"Constr_(8)_for_pairB_" + xind.xi + "_" + xind.xj+"_station_"+xind.staz+"_"+p);
			conta_vincoli++;
		}
	}

	// add constr 9 ----------------------------------------------------
	/*for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp = model.linearNumExpr();
		exp.addTerm(1, b.get(xind));
		exp.addTerm(-1/speed_min, x.get(xind));
		ArrayList<RechargeTypes> tecStaz = RRT.get(xind.staz);

		for (RechargeTypes rtyStaz: tecStaz){
			IJSRIndex ijsr = new IJSRIndex(xind.xi,xind.xj,xind.staz, rtyStaz.id);
			if(IJSRindici.contains(ijsr))
			{
				exp.addTerm(-1 / rtyStaz.speed, beta.get(ijsr));
			}
		}
		model.addGe(exp, -1/speed_min, "Constr_(9)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
		conta_vincoli++;
	}*/
	for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp = model.linearNumExpr();
		IloLinearNumExpr exp1 = model.linearNumExpr();

		exp.addTerm(1, b.get(xind));
		exp1.addTerm(1, b.get(xind));

		exp.addTerm(-1/speed_min, x.get(xind));
		exp1.addTerm(1/speed_min, x.get(xind));

		ArrayList<RechargeTypes> tecStaz = RRT.get(xind.staz);

		for (RechargeTypes rtyStaz: tecStaz){
			IJSRIndex ijsr = new IJSRIndex(xind.xi,xind.xj,xind.staz, rtyStaz.id);
			if(IJSRindici.contains(ijsr))
			{
				exp.addTerm(-1 / rtyStaz.speed, beta.get(ijsr));
				exp1.addTerm(-1 / rtyStaz.speed, beta.get(ijsr));
			}
		}
		model.addGe(exp, -1/speed_min, "Constr_(9a)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
		model.addLe(exp1, 1/speed_min, "Constr_(9b)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
		conta_vincoli += 2;
	}

	// add constr 10 -------------------------------------------------
	for (int i=1;i<Dep.length;i++) {
		String dep = Dep[i].id;

		for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius") && e.xi.equals(dep)).collect(Collectors.toList()))
		//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		//	if (varX.getKey().staz.equals("fictius")&&varX.getKey().xi.contains("D"))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, a.get(xind.xj));
			exp.addTerm(-1, t.get(xind));
			double bigM = (NodesMap.get("D0").s + 60*D.get("D0").get(xind.xj))/v_min.get(new IJind(xind.xi,xind.xj));
			exp.addTerm(-bigM, x.get(xind));
			model.addGe(exp, -bigM, "Constr_(10)_for_pair_" + xind.xi+"_"+xind.xj);
			conta_vincoli++;
		}
	}


	// add constr 11 ----------------------------------------------------
	for (int i=1;i<Dep.length;i++) {
		String dep = Dep[i].id;

		for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && e.xi.equals(dep)).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, a.get(xind.xj));
			exp.addTerm(-1, ta.get(xind));
			exp.addTerm(-1, tb.get(xind));
			exp.addTerm(-1, b.get(xind));
			double bigM = (NodesMap.get(xind.xi).s + 60*D.get(xind.xi).get(xind.staz)/v_min.get(new IJind(xind.xi,xind.staz)) +
					60*D.get(xind.staz).get(xind.xj)/v_min.get(new IJind(xind.staz,xind.xj)) + 1/speed_min);

			exp.addTerm(-bigM, x.get(xind));

			model.addGe(exp, -bigM, "Constr_(11)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
			conta_vincoli++;
		}
	}

	// add constr 12a ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius") && !e.xi.contains("D") && e.xj.contains("D") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp= model.linearNumExpr();
		exp.addTerm(1, a.get(xind.xj));
		exp.addTerm(-1, a.get(xind.xi));
		exp.addTerm(-1, t.get(xind));
		exp.addTerm(-1, w.get(xind.xi));
		double bigM = NodesMap.get("D0").e;

		exp.addTerm(-bigM, x.get(xind));

		model.addGe(exp, NodesMap.get(xind.xi).service_time -bigM, "Constr_(12a)_for_pair_"+xind.xi+"_"+xind.xj);
		conta_vincoli++;
	}

	// add constr 12b ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius") && !e.xi.contains("D") && e.xj.contains("D") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp= model.linearNumExpr();
		exp.addTerm(1, a.get(xind.xj));
		exp.addTerm(-1, a.get(xind.xi));
		exp.addTerm(-1, t.get(xind));
		exp.addTerm(-1, w.get(xind.xi));
		double bigM = NodesMap.get("D0").e;

		exp.addTerm(bigM, x.get(xind));

		model.addLe(exp,NodesMap.get(xind.xi).service_time+bigM, "Constr_(12b)_for_pair_"+xind.xi+"_"+xind.xj);
		conta_vincoli++;
	}
	// add constr 13a --------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && !e.xi.contains("D") && e.xj.contains("D") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp = model.linearNumExpr();
		exp.addTerm(1, a.get(xind.xj));
		exp.addTerm(-1, a.get(xind.xi));
		exp.addTerm(-1, ta.get(xind));
		exp.addTerm(-1, tb.get(xind));
		exp.addTerm(-1, w.get(xind.xi));
		exp.addTerm(-1, b.get(xind));
		double bigM = NodesMap.get("D0").e;

		exp.addTerm(-bigM, x.get(xind));

		model.addGe(exp, NodesMap.get(xind.xi).service_time - bigM, "Constr_(13a)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
		conta_vincoli++;
	}

	// add constr 13b ---------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && !e.xi.contains("D") && e.xj.contains("D") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp= model.linearNumExpr();
		exp.addTerm(1, a.get(xind.xj));
		exp.addTerm(-1, a.get(xind.xi));
		exp.addTerm(-1, ta.get(xind));
		exp.addTerm(-1, tb.get(xind));
		exp.addTerm(-1, w.get(xind.xi));
		exp.addTerm(-1, b.get(xind));
		double bigM = NodesMap.get("D0").e;

		exp.addTerm(bigM, x.get(xind));

		model.addLe(exp,NodesMap.get(xind.xi).service_time+bigM, "Constr_(13b)_for_pair_"+xind.xi+"_"+xind.xj+"_station_"+xind.staz);
		conta_vincoli++;
	}

	// add cosntr 14 ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		for(int q=0;q<Q.length;q++) {
			IloLinearNumExpr exp= model.linearNumExpr();
			IJind ij = new IJind(xind.xi, xind.xj);
			exp.addTerm(1, e.get(xind));
			exp.addTerm(-kq[q], v.get(xind));
			exp.addTerm(-phi, f.get(ij));
			exp.addTerm(-1, x.get(xind));
			model.addGe(exp, bq[q]-1, "Constr_(14)_for_pair_"+ij.xi+"_"+ij.xj);
			conta_vincoli++;
		}
	}

	// add cosntr 15 ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		for (int q = 0; q < Q.length; q++) {
			IloLinearNumExpr exp = model.linearNumExpr();
			IJind ij = new IJind(xind.xi, xind.xj);
			exp.addTerm(1, ea.get(xind));
			exp.addTerm(-kq[q], va.get(xind));
			exp.addTerm(-phi, f.get(ij));
			exp.addTerm(-1, x.get(xind));
			model.addGe(exp, bq[q] - 1, "Constr_(15)_for_pair_" + ij.xi + "_" + ij.xj + "_station_" + xind.staz);
			conta_vincoli++;
		}
	}

	// add constr 16 ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		for (int q = 0; q < Q.length; q++) {
			IloLinearNumExpr exp = model.linearNumExpr();
			IJind ij = new IJind(xind.xi, xind.xj);
			exp.addTerm(1, eb.get(xind));
			exp.addTerm(-kq[q], vb.get(xind));
			exp.addTerm(-phi, f.get(ij));
			exp.addTerm(-1, x.get(xind));
			model.addGe(exp, bq[q] - 1, "Constr_(16)_for_pair_" + ij.xi + "_" + ij.xj + "_station_" + xind.staz);
			conta_vincoli++;
		}
	}

	add_constraint_17_();

	// Add constr 18 ------------------------------------------------------------
	for(int v=1;v<Dep.length;v++) {
		String dep = Dep[v].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(xind.xj));
			exp.addTerm(-1, r_v_0.get(xind.xi));
			exp.addTerm(D.get(xind.xi).get(xind.xj), e.get(xind));
			exp.addTerm(1, x.get(xind)); // -1
			model.addLe(exp, 1, "Constr_(18)_for_pair_" +xind.xi+"_"+xind.xj ); //Ge -1
			conta_vincoli++;
		}
	}

	// Add constr 19 -----------------------------------------------------------
	for(int v=1;v<Dep.length;v++) {
		String dep = Dep[v].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r_v_0.get(xind.xi));
			exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
			exp.addTerm(-1, x.get(xind));
			model.addGe(exp, sigma - 1, "Constr_(19)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz); // mancava sigma
			conta_vincoli++;
		}
	}

// add constr 20a 20b -----------------------------------------------------------
	for(int v=0;v<Dep.length;v++) {
		String dep = Dep[v].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1 = model.linearNumExpr();

			exp.addTerm(1, r.get(xind.xj));
			exp.addTerm(-1, r_v_0.get(xind.xi));
			exp.addTerm(D.get(xind.xi).get(xind.staz), ea.get(xind));
			exp.addTerm(D.get(xind.staz).get(xind.xj), eb.get(xind));
			exp.addTerm(1, x.get(xind));

			exp1.addTerm(1, r.get(xind.xj));
			exp1.addTerm(-1, r_v_0.get(xind.xi));
			exp1.addTerm(D.get(xind.xi).get(xind.staz), ea.get(xind));
			exp1.addTerm(D.get(xind.staz).get(xind.xj), eb.get(xind));
			exp1.addTerm(-1, x.get(xind));

			for (RechargeTypes rtyStaz: RRT.get(xind.staz)) {
				IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rtyStaz.id);
				if (IJSRindici.contains(ijsr)) {
					exp.addTerm(-1, beta.get(ijsr));
					exp1.addTerm(-1, beta.get(ijsr));
				}
			}
			//	}
			model.addLe(exp, 1, "Constr_(20a)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
			model.addGe(exp1, -1, "Constr_(20b)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
			conta_vincoli +=2;
		}
	}

// add constr 21 -----------------------------------------------------------
	for(int v=1;v<Dep.length;v++) {
		String dep = Dep[v].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r_v_0.get(xind.xi));
			exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
			//exp.addTerm(-D.get(xind.staz).get(xind.xj), eb.get(xind));
			exp.addTerm(-1, x.get(xind));
			model.addGe(exp, sigma-1, "Constr_(21)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
			conta_vincoli++;
		}
	}

// add constr 22 -----------------------------------------------------------
	for(int v=1;v<Dep.length;v++) {
		String dep = Dep[v].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r_v_0.get(xind.xi));
			exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
			exp.addTerm(1, x.get(xind));

			for(RechargeTypes rt:RRT.get(xind.staz)) {
				IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
				if (IJSRindici.contains(ijsr)) {
					exp.addTerm(1, beta.get(ijsr));
				}
			}
			model.addLe(exp, gamma+1, "Constr_(22)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
			conta_vincoli++;
		}
	}

	// add constr 28a e 28b ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e-> e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp = model.linearNumExpr();
		IJind ij = new IJind(xind.xi, xind.xj);
		exp.addTerm(1, v.get(xind));
		exp.addTerm(-v_max.get(ij), x.get(xind));
		model.addLe(exp, 0, "Constr_(28a)_for_pair_" + ij.xi + "_" + ij.xj);
		IloLinearNumExpr exp1 = model.linearNumExpr();
		exp1.addTerm(1, v.get(xind));
		exp1.addTerm(-v_min.get(ij), x.get(xind));
		model.addGe(exp1, 0, "Constr_(28b)_for_pair_" + ij.xi + "_" + ij.xj);
		conta_vincoli += 2;
	}

	// add constr 29 ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e-> !e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp = model.linearNumExpr();
		IloLinearNumExpr exp1= model.linearNumExpr();
		exp.addTerm(1, va.get(xind));
		exp1.addTerm(1, va.get(xind));
		exp.addTerm(-v_max.get(new IJind(xind.xi,xind.staz)), x.get(xind));
		exp1.addTerm(-v_min.get(new IJind(xind.xi,xind.staz)), x.get(xind));
		model.addLe(exp, 0, "Constr_(29)_for_customer_"+ xind.xi+"_station_"+ xind.staz);
		model.addGe(exp1, 0, "Constr_(29)_for_customer_"+ xind.xi+"_station_"+ xind.staz);
		conta_vincoli += 2;
	}

	// add constr 30 ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e-> !e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp = model.linearNumExpr();
		IloLinearNumExpr exp1= model.linearNumExpr();
		exp.addTerm(1, vb.get(xind));
		exp1.addTerm(1, vb.get(xind));
		exp.addTerm(-v_max.get(new IJind(xind.staz,xind.xj)), x.get(xind));
		exp1.addTerm(-v_min.get(new IJind(xind.staz,xind.xj)), x.get(xind));
		model.addLe(exp, 0, "Constr_(30)_for_customer_"+ xind.staz+"_station_"+ xind.xj);
		model.addGe(exp1, 0, "Constr_(30)_for_customer_"+ xind.staz+"_station_"+ xind.xj);
		conta_vincoli += 2;
	}

	// update constr 31
	for(int i=1;i<N.length;i++) {
		String nodoCust = N[i].id;

		IloLinearNumExpr exp= model.linearNumExpr();
		for(IJind find: Findici.stream().filter(e-> e.xj.equals(nodoCust) && e.xi.contains("D") && !e.xi.equals("D0")).collect(Collectors.toList())) {
			exp.addTerm(1, f.get(find));
		}

		for(IJind find: Findici.stream().filter(e-> e.xi.equals(nodoCust) && e.xj.contains("D") && !e.xj.equals("D0")).collect(Collectors.toList())) {
			exp.addTerm(-1, f.get(find));
		}
		model.addToExpr(Constr31Range[i],exp );
	}

	// add constr 32 ----------------------------------------------------
	for(Map.Entry<IJind,IloNumVar> varF: f.entrySet()){
		if((varF.getKey().xi.contains("D") || varF.getKey().xj.contains("D")) && !varF.getKey().xi.equals("D0") && !varF.getKey().xj.equals("D0")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, f.get(varF.getKey()));

			for (XIndex xKey : Xindici.stream().filter(e -> e.xi.equals(varF.getKey().xi) && e.xj.equals(varF.getKey().xj)).collect(Collectors.toList())) {
				exp.addTerm(-Cl, x.get(xKey));
			}
			model.addLe(exp, 0, "Constr_(32)_for_pair_" + varF.getKey().xi + "_" + varF.getKey().xj);
			conta_vincoli++;
		}
	}

	// add constr 34 ----------------------------------------------------
	for(int v=1;v<Dep.length;v++) {
		String dep = Dep[v].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			if(!xind.xi.contains("D")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
				exp.addTerm(-1, x.get(xind));

				model.addGe(exp, sigma-1, "Constr_(34)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli++;
			}
		}
	}

// add constr 35 ----------------------------------------------------
	for(int v=1;v<Dep.length;v++) {
		String dep = Dep[v].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			if(!xind.xi.contains("D")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
				for(RechargeTypes rt:RRT.get(xind.staz)) {
					IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
					if (IJSRindici.contains(ijsr)) {
						exp.addTerm(1, beta.get(ijsr));
					}
				}
				exp.addTerm(1, x.get(xind));

				model.addLe(exp, gamma+1, "Constr_(35)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli++;
			}
		}
	}

	// add constr 36 ----------------------------------------------------
	for(int v=1;v<Dep.length;v++) {
		String dep = Dep[v].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			if(!xind.xi.contains("D")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
				for(RechargeTypes rt:RRT.get(xind.staz)) {
					IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
					if (IJSRindici.contains(ijsr)) {
						exp.addTerm(1, beta.get(ijsr));
					}
				}
				exp.addTerm(-D.get(xind.staz).get(xind.xj), eb.get(xind));
				exp.addTerm(-1, x.get(xind));

				model.addGe(exp, sigma-1, "Constr_(36)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli++;
			}
		}
	}

// add constr 37 --------------------------------------------
	for(int v=1;v<Dep.length;v++) {
		String dep = Dep[v].id;

		for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep) && e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			if(!xind.xi.contains("D")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.xj), e.get(xind));
				exp.addTerm(-1, x.get(xind));

				model.addGe(exp, sigma-1, "Constr_(37a)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli++;
			}
		}
	}

	// update objective ********************************
	// Costo veicoli
	model.setLinearCoef(obiett,violK,0.0); // azzero il coeff della violK
	model.setLinearCoef(obiett,CostoVeicoliInitSol,0.0);

	IloLinearNumExpr obCV= model.linearNumExpr();
	obCV.addTerm(1.0,CostoVeicoli);

	for(XIndex xind:x.keySet()){
		if(xind.xi.contains("D")){
			obCV.addTerm(-FC, x.get(xind));
		}
	}
	defCostoVei = model.addEq(obCV,0.0,"CostoVe");

	IloLinearNumExpr obCostVe = model.linearNumExpr();
	obCostVe.addTerm(1.0,CostoVeicoli);
	model.addToExpr(obiett,obCostVe );

	// Costo Energia ---------------------------------
	model.setLinearCoef(obiett,CostoEnergiaInitSol,0.0);

	IloLinearNumExpr obCE= model.linearNumExpr();
	obCE.addTerm(1.0,CostoEnergia);
	for(node n:Dep)//solo una tecnologia disponibile al deposito
	{
		obCE.addTerm(-Cb*FE*RRT.get("S0").get(0).cost, r_v_0.get(n.id));
	}

	for(IJSRIndex ijsr:IJSRindici) {
		double costoEn = RRT.get(ijsr.staz).stream().filter(e-> e.id== ijsr.tec).findFirst().get().cost;
		obCE.addTerm(-Cb*FE*costoEn, beta.get(ijsr));
	}
	defCostoEn = model.addEq(obCE,0.0,"CostoEn");

	IloLinearNumExpr obCostEn = model.linearNumExpr();
	obCostEn.addTerm(1.0,CostoEnergia);
	model.addToExpr(obiett,obCostEn );

	// Costo drivers ---------------------------------
	model.setLinearCoef(obiett,CostoDriversInitSol,0.0);
	IloLinearNumExpr obCD= model.linearNumExpr();
	obCD.addTerm(1.0,CostoDrivers);

	for(XIndex ijs: Xindici){
		if(ijs.staz.equals("fictius")) {
			obCD.addTerm(-FD, t.get(ijs));
		}
		else {
			obCD.addTerm(-FD, ta.get(ijs));
			obCD.addTerm(-FD, tb.get(ijs));
			obCD.addTerm(-FD, b.get(ijs));
		}
	}

	for(int i=0;i<N.length;i++) {
		obCD.addTerm(-FD, w.get(N[i].id));
	}
	defCostoDrivers = model.addEq(obCD,0.0,"CostoDrv");

	IloLinearNumExpr obCostDrv = model.linearNumExpr();
	obCostDrv.addTerm(1.0,CostoDrivers);
	model.addToExpr(obiett,obCostDrv );

}

public void update_constraints_2_3(int addedDeps) throws IloException {
		int ind = num_veic-addedDeps;
		//  update constraints 2 ----------------------------------------------------
		for (int j = 0; j < N.length; j++) {
			IloLinearNumExpr exp = model.linearNumExpr();
			String nodoj = N[j].id;
			for (XIndex xind : Xindici.stream().filter(e -> e.xj.equals(nodoj) && e.xi.equals("D0")).collect(Collectors.toList())) {
				for(int i=ind;i<num_veic;i++) {
					XIndex idep = new XIndex("D"+i,xind.xj, xind.staz);
					exp.addTerm(1, x.get(idep));
				}
			}
			model.addToExpr(Constr2Range[j], exp);
		}

		//  update constraints 3 ----------------------------------------------------
		for (int i = 0; i < N.length; i++) {
			IloLinearNumExpr exp = model.linearNumExpr();
			String nodoi = N[i].id;
			for (XIndex xind : Xindici.stream().filter(e -> e.xi.equals(nodoi) && e.xj.equals("D0")).collect(Collectors.toList())) {
				for(int j=ind;j<num_veic;j++) {
					XIndex idep = new XIndex(xind.xi, "D"+j, xind.staz);
					exp.addTerm(1, x.get(idep));
				}
			}
			model.addToExpr(Constr3Range[i], exp);
		}
	}

/*
public void add_new_constraints() throws IloException {

		//  update constraints 2 ----------------------------------------------------
		for(int j=0;j<N.length;j++) {
			IloLinearNumExpr exp = model.linearNumExpr();
			String nodoj = N[j].id;
			for (XIndex xind : Xindici_add.stream().collect(Collectors.toList())) {
				exp.addTerm(1, x.get(xind));
			}
			model.addToExpr(Constr2Range[j],exp );
		}

		//  update constraints 3 ----------------------------------------------------
		for(int i=0;i<N.length;i++) {
			IloLinearNumExpr exp= model.linearNumExpr();
			String nodoi = N[i].id;
			for (XIndex xind:Xindici_add.stream().collect(Collectors.toList()))
			{
				exp.addTerm(1,x.get(xind));
			}
			model.addToExpr(Constr3Range[i],exp );
		}

		model.delete(constr4bis); // elimino constraint 4bis

		add_constraint_4_();

		//add constraints 5 ----------------------------------------------------
		for (int i=1;i<Dep.length;i++) {
			IloLinearNumExpr exp= model.linearNumExpr();
			String dep = Dep[i].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xi.equals(dep)).collect(Collectors.toList()))
			{
				exp.addTerm(1,x.get(xind));
			}
			for(XIndex xind: Xindici_add.stream().filter(e->e.xj.equals(dep)).collect(Collectors.toList()))
			{
				exp.addTerm(-1,x.get(xind));
			}
			model.addEq(exp, 0, "Constr_(5)_for_depot_"+Dep[i].id);
			conta_vincoli++;
		}

		// add constr (6) ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e->e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			for (int p = 0; p < P.length; p++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, t.get(xind));
				exp.addTerm(-60 * D.get(xind.xi).get(xind.xj) * kp[p], v.get(xind));
				double bigM = 60 * D.get(xind.xi).get(xind.xj) * (Math.max(kp[0]*v_min.get(new IJind(xind.xi,xind.xj)),0))+
						60 * D.get(xind.xi).get(xind.xj)*bp[0];
				exp.addTerm(-bigM, x.get(xind));

				model.addGe(exp, 60 * D.get(xind.xi).get(xind.xj) * bp[p]-bigM,"Constr_(6)_for_pair_" + xind.xi + "_" + xind.xj+"_"+p);
				conta_vincoli++;
			}
		}

		// add constr 7 ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e->!e.staz.equals("fictius") ).collect(Collectors.toList()))
		{
			for (int p = 0; p < P.length; p++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, ta.get(xind));
				exp.addTerm(-60 * D.get(xind.xi).get(xind.staz) * kp[p], va.get(xind));
				double bigM = 60 * D.get(xind.xi).get(xind.staz) * (Math.max(kp[0]*v_min.get(new IJind(xind.xi,xind.staz)),0))+
						60 * D.get(xind.xi).get(xind.staz)*bp[0];
				exp.addTerm(-bigM, x.get(xind));

				model.addGe(exp, 60 * D.get(xind.xi).get(xind.staz) * bp[p] -bigM,
						"Constr_(7)_for_pairA_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz + "_" + p);
				conta_vincoli++;
			}
		}

		// add constr 8 ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e->!e.staz.equals("fictius") ).collect(Collectors.toList()))
		{
			for (int p = 0; p < P.length; p++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp = model.linearNumExpr();
				exp.addTerm(1, tb.get(xind));
				exp.addTerm(-60 * D.get(xind.staz).get(xind.xj) * kp[p], vb.get(xind));
				double bigM = 60 * D.get(xind.staz).get(xind.xj) * (Math.max(kp[0]*v_min.get(new IJind(xind.staz,xind.xj)),0))+
						60 * D.get(xind.staz).get(xind.xj)*bp[0];

				exp.addTerm(-bigM, x.get(xind));

				model.addGe(exp,60 * D.get(xind.staz).get(xind.xj) * bp[p]	-bigM,
						"Constr_(8)_for_pairB_" + xind.xi + "_" + xind.xj+"_station_"+xind.staz+"_"+p);
				conta_vincoli++;
			}
		}

		// add constr 9 ----------------------------------------------------
	for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && (e.xi.contains("D") || e.xj.contains("D")) && !e.xi.equals("D0") && !e.xj.equals("D0")).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp = model.linearNumExpr();
		exp.addTerm(1, b.get(xind));
		exp.addTerm(-1/speed_min, x.get(xind));
		ArrayList<RechargeTypes> tecStaz = RRT.get(xind.staz);

		for (RechargeTypes rtyStaz: tecStaz){
			IJSRIndex ijsr = new IJSRIndex(xind.xi,xind.xj,xind.staz, rtyStaz.id);
			if(IJSRindici.contains(ijsr))
			{
				exp.addTerm(-1 / rtyStaz.speed, beta.get(ijsr));
			}
		}
		model.addGe(exp, -1/speed_min, "Constr_(9)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
		conta_vincoli++;
	}
		for(XIndex xind: Xindici_add.stream().filter(e->!e.staz.equals("fictius") ).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1 = model.linearNumExpr();

			exp.addTerm(1, b.get(xind));
			exp1.addTerm(1, b.get(xind));

			exp.addTerm(-1/speed_min, x.get(xind));
			exp1.addTerm(1/speed_min, x.get(xind));

			ArrayList<RechargeTypes> tecStaz = RRT.get(xind.staz);

			for (RechargeTypes rtyStaz: tecStaz){
				IJSRIndex ijsr = new IJSRIndex(xind.xi,xind.xj,xind.staz, rtyStaz.id);
				if(IJSRindici_add.contains(ijsr))
				{
					exp.addTerm(-1 / rtyStaz.speed, beta.get(ijsr));
					exp1.addTerm(-1 / rtyStaz.speed, beta.get(ijsr));
				}
			}
			model.addGe(exp, -1/speed_min, "Constr_(9a)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
			model.addLe(exp1, 1/speed_min, "Constr_(9b)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
			conta_vincoli += 2;
		}

		// add constr 10 -------------------------------------------------
		for (int i=1;i<Dep.length;i++) {
			String dep = Dep[i].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.staz.equals("fictius") && e.xi.equals(dep)).collect(Collectors.toList()))
			//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
			//	if (varX.getKey().staz.equals("fictius")&&varX.getKey().xi.contains("D"))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, a.get(xind.xj));
				exp.addTerm(-1, t.get(xind));
				double bigM = (NodesMap.get("D0").s + 60*D.get("D0").get(xind.xj))/v_min.get(new IJind(xind.xi,xind.xj));
				exp.addTerm(-bigM, x.get(xind));
				model.addGe(exp, -bigM, "Constr_(10)_for_pair_" + xind.xi+"_"+xind.xj);
				conta_vincoli++;
			}
		}


		// add constr 11 ----------------------------------------------------
		for (int i=1;i<Dep.length;i++) {
			String dep = Dep[i].id;

			for(XIndex xind: Xindici_add.stream().filter(e->!e.staz.equals("fictius") && e.xi.equals(dep)).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, a.get(xind.xj));
				exp.addTerm(-1, ta.get(xind));
				exp.addTerm(-1, tb.get(xind));
				exp.addTerm(-1, b.get(xind));
				double bigM = (NodesMap.get(xind.xi).s + 60*D.get(xind.xi).get(xind.staz)/v_min.get(new IJind(xind.xi,xind.staz)) +
						60*D.get(xind.staz).get(xind.xj)/v_min.get(new IJind(xind.staz,xind.xj)) + 1/speed_min);

				exp.addTerm(-bigM, x.get(xind));

				model.addGe(exp, -bigM, "Constr_(11)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
				conta_vincoli++;
			}
		}

		// add constr 12a ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e->e.staz.equals("fictius") && !e.xi.startsWith("D")  ).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, a.get(xind.xj));
			exp.addTerm(-1, a.get(xind.xi));
			exp.addTerm(-1, t.get(xind));
			exp.addTerm(-1, w.get(xind.xi));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(-bigM, x.get(xind));

			model.addGe(exp, NodesMap.get(xind.xi).service_time -bigM, "Constr_(12a)_for_pair_"+xind.xi+"_"+xind.xj);
			conta_vincoli++;
		}

		// add cosntr 12b ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e->e.staz.equals("fictius") && !e.xi.startsWith("D")  ).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, a.get(xind.xj));
			exp.addTerm(-1, a.get(xind.xi));
			exp.addTerm(-1, t.get(xind));
			exp.addTerm(-1, w.get(xind.xi));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(bigM, x.get(xind));

			model.addLe(exp,NodesMap.get(xind.xi).service_time+bigM, "Constr_(12b)_for_pair_"+xind.xi+"_"+xind.xj);
			conta_vincoli++;
		}

	// add constr 13a --------------------------------------------------
	for(XIndex xind: Xindici_add.stream().filter(e->!e.staz.equals("fictius") && !e.xi.startsWith("D") ).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp = model.linearNumExpr();
		exp.addTerm(1, a.get(xind.xj));
		exp.addTerm(-1, a.get(xind.xi));
		exp.addTerm(-1, ta.get(xind));
		exp.addTerm(-1, tb.get(xind));
		exp.addTerm(-1, w.get(xind.xi));
		exp.addTerm(-1, b.get(xind));
		double bigM = NodesMap.get("D0").e;

		exp.addTerm(-bigM, x.get(xind));

		model.addGe(exp, NodesMap.get(xind.xi).service_time - bigM, "Constr_(13a)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
		conta_vincoli++;
	}

	// add constr 13b ---------------------------------------------------
	for(XIndex xind: Xindici_add.stream().filter(e->!e.staz.equals("fictius") && !e.xi.startsWith("D") ).collect(Collectors.toList()))
	{
		IloLinearNumExpr exp= model.linearNumExpr();
		exp.addTerm(1, a.get(xind.xj));
		exp.addTerm(-1, a.get(xind.xi));
		exp.addTerm(-1, ta.get(xind));
		exp.addTerm(-1, tb.get(xind));
		exp.addTerm(-1, w.get(xind.xi));
		exp.addTerm(-1, b.get(xind));
		double bigM = NodesMap.get("D0").e;

		exp.addTerm(bigM, x.get(xind));

		model.addLe(exp,NodesMap.get(xind.xi).service_time+bigM, "Constr_(13b)_for_pair_"+xind.xi+"_"+xind.xj+"_station_"+xind.staz);
		conta_vincoli++;
	}

		// add cosntr 14 ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e->e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			for(int q=0;q<Q.length;q++) {
				IloLinearNumExpr exp= model.linearNumExpr();
				IJind ij = new IJind(xind.xi, xind.xj);
				exp.addTerm(1, e.get(xind));
				exp.addTerm(-kq[q], v.get(xind));
				exp.addTerm(-phi, f.get(ij));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, bq[q]-1, "Constr_(14)_for_pair_"+ij.xi+"_"+ij.xj);
				conta_vincoli++;
			}
		}

		// add cosntr 15 ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e->!e.staz.equals("fictius") ).collect(Collectors.toList()))
		{
			for (int q = 0; q < Q.length; q++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				IJind ij = new IJind(xind.xi, xind.xj);
				exp.addTerm(1, ea.get(xind));
				exp.addTerm(-kq[q], va.get(xind));
				exp.addTerm(-phi, f.get(ij));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, bq[q] - 1, "Constr_(15)_for_pair_" + ij.xi + "_" + ij.xj + "_station_" + xind.staz);
				conta_vincoli++;
			}
		}

		// add constr 16 ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e->!e.staz.equals("fictius") ).collect(Collectors.toList()))
		{
			for (int q = 0; q < Q.length; q++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				IJind ij = new IJind(xind.xi, xind.xj);
				exp.addTerm(1, eb.get(xind));
				exp.addTerm(-kq[q], vb.get(xind));
				exp.addTerm(-phi, f.get(ij));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, bq[q] - 1, "Constr_(16)_for_pair_" + ij.xi + "_" + ij.xj + "_station_" + xind.staz);
				conta_vincoli++;
			}
		}

		add_constraint_17_();

		// Add constr 18 ------------------------------------------------------------
		for(int v=1;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xi.equals(dep) && e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r.get(xind.xj));
				exp.addTerm(-1, r_v_0.get(xind.xi));
				exp.addTerm(D.get(xind.xi).get(xind.xj), e.get(xind));
				exp.addTerm(1, x.get(xind)); // -1
				model.addLe(exp, 1, "Constr_(18)_for_pair_" +xind.xi+"_"+xind.xj ); //Ge -1
				conta_vincoli++;
			}
		}

		// Add constr 19 -----------------------------------------------------------
		for(int v=1;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r_v_0.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, sigma - 1, "Constr_(19)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz); // mancava sigma
				conta_vincoli++;
			}
		}

// add constr 20a 20b -----------------------------------------------------------
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				IloLinearNumExpr exp1 = model.linearNumExpr();

				exp.addTerm(1, r.get(xind.xj));
				exp.addTerm(-1, r_v_0.get(xind.xi));
				exp.addTerm(D.get(xind.xi).get(xind.staz), ea.get(xind));
				exp.addTerm(D.get(xind.staz).get(xind.xj), eb.get(xind));
				exp.addTerm(1, x.get(xind));

				exp1.addTerm(1, r.get(xind.xj));
				exp1.addTerm(-1, r_v_0.get(xind.xi));
				exp1.addTerm(D.get(xind.xi).get(xind.staz), ea.get(xind));
				exp1.addTerm(D.get(xind.staz).get(xind.xj), eb.get(xind));
				exp1.addTerm(-1, x.get(xind));

				for (RechargeTypes rtyStaz: RRT.get(xind.staz)) {
					IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rtyStaz.id);
					if (IJSRindici_add.contains(ijsr)) {
						exp.addTerm(-1, beta.get(ijsr));
						exp1.addTerm(-1, beta.get(ijsr));
					}
				}
				//	}
				model.addLe(exp, 1, "Constr_(20a)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				model.addGe(exp1, -1, "Constr_(20b)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli +=2;
			}
		}

// add constr 21 -----------------------------------------------------------
		for(int v=1;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r_v_0.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
				//exp.addTerm(-D.get(xind.staz).get(xind.xj), eb.get(xind));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, sigma-1, "Constr_(21)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli++;
			}
		}

// add constr 22 -----------------------------------------------------------
		for(int v=1;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r_v_0.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
				exp.addTerm(1, x.get(xind));

				for(RechargeTypes rt:RRT.get(xind.staz)) {
					IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
					if (IJSRindici_add.contains(ijsr)) {
						exp.addTerm(1, beta.get(ijsr));
					}
				}
				model.addLe(exp, gamma+1, "Constr_(22)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli++;
			}
		}

		// add constr 28a e 28b ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e-> e.staz.equals("fictius") ).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			IJind ij = new IJind(xind.xi, xind.xj);
			exp.addTerm(1, v.get(xind));
			exp.addTerm(-v_max.get(ij), x.get(xind));
			model.addLe(exp, 0, "Constr_(28a)_for_pair_" + ij.xi + "_" + ij.xj);
			IloLinearNumExpr exp1 = model.linearNumExpr();
			exp1.addTerm(1, v.get(xind));
			exp1.addTerm(-v_min.get(ij), x.get(xind));
			model.addGe(exp1, 0, "Constr_(28b)_for_pair_" + ij.xi + "_" + ij.xj);
			conta_vincoli += 2;
		}

		// add constr 29 ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e-> !e.staz.equals("fictius") ).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1= model.linearNumExpr();
			exp.addTerm(1, va.get(xind));
			exp1.addTerm(1, va.get(xind));
			exp.addTerm(-v_max.get(new IJind(xind.xi,xind.staz)), x.get(xind));
			exp1.addTerm(-v_min.get(new IJind(xind.xi,xind.staz)), x.get(xind));
			model.addLe(exp, 0, "Constr_(29)_for_customer_"+ xind.xi+"_station_"+ xind.staz);
			model.addGe(exp1, 0, "Constr_(29)_for_customer_"+ xind.xi+"_station_"+ xind.staz);
			conta_vincoli += 2;
		}

		// add constr 30 ----------------------------------------------------
		for(XIndex xind: Xindici_add.stream().filter(e-> !e.staz.equals("fictius") ).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1= model.linearNumExpr();
			exp.addTerm(1, vb.get(xind));
			exp1.addTerm(1, vb.get(xind));
			exp.addTerm(-v_max.get(new IJind(xind.staz,xind.xj)), x.get(xind));
			exp1.addTerm(-v_min.get(new IJind(xind.staz,xind.xj)), x.get(xind));
			model.addLe(exp, 0, "Constr_(30)_for_customer_"+ xind.staz+"_station_"+ xind.xj);
			model.addGe(exp1, 0, "Constr_(30)_for_customer_"+ xind.staz+"_station_"+ xind.xj);
			conta_vincoli += 2;
		}

		// update constr 31
		for(int i=1;i<N.length;i++) {
			String nodoCust = N[i].id;

			IloLinearNumExpr exp= model.linearNumExpr();
			for(IJind find: Findici_add.stream().filter(e-> e.xj.equals(nodoCust) ).collect(Collectors.toList())) {
				exp.addTerm(1, f.get(find));
			}

			for(IJind find: Findici_add.stream().filter(e-> e.xi.equals(nodoCust) ).collect(Collectors.toList())) {
				exp.addTerm(-1, f.get(find));
			}
			model.addToExpr(Constr31Range[i],exp );
		}

		// add constr 32 ----------------------------------------------------
		for(Map.Entry<IJind,IloNumVar> varF: f.entrySet()){
			if((varF.getKey().xi.contains("D") || varF.getKey().xj.contains("D")) && !varF.getKey().xi.equals("D0") && !varF.getKey().xj.equals("D0")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, f.get(varF.getKey()));

				for (XIndex xKey : Xindici_add.stream().filter(e -> e.xi.equals(varF.getKey().xi) && e.xj.equals(varF.getKey().xj)).collect(Collectors.toList())) {
					exp.addTerm(-Cl, x.get(xKey));
				}
				model.addLe(exp, 0, "Constr_(32)_for_pair_" + varF.getKey().xi + "_" + varF.getKey().xj);
				conta_vincoli++;
			}
		}

		// add constr 34 ----------------------------------------------------
		for(int v=1;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xj.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				if(!xind.xi.contains("D")) {
					IloLinearNumExpr exp = model.linearNumExpr();
					exp.addTerm(1, r.get(xind.xi));
					exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
					exp.addTerm(-1, x.get(xind));

					model.addGe(exp, sigma-1, "Constr_(34)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
					conta_vincoli++;
				}
			}
		}

// add constr 35 ----------------------------------------------------
		for(int v=1;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xj.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				if(!xind.xi.contains("D")) {
					IloLinearNumExpr exp = model.linearNumExpr();
					exp.addTerm(1, r.get(xind.xi));
					exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
					for(RechargeTypes rt:RRT.get(xind.staz)) {
						IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
						if (IJSRindici_add.contains(ijsr)) {
							exp.addTerm(1, beta.get(ijsr));
						}
					}
					exp.addTerm(1, x.get(xind));

					model.addLe(exp, gamma+1, "Constr_(35)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
					conta_vincoli++;
				}
			}
		}

		// add constr 36 ----------------------------------------------------
		for(int v=1;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xj.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				if(!xind.xi.contains("D")) {
					IloLinearNumExpr exp = model.linearNumExpr();
					exp.addTerm(1, r.get(xind.xi));
					exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
					for(RechargeTypes rt:RRT.get(xind.staz)) {
						IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
						if (IJSRindici_add.contains(ijsr)) {
							exp.addTerm(1, beta.get(ijsr));
						}
					}
					exp.addTerm(-D.get(xind.staz).get(xind.xj), eb.get(xind));
					exp.addTerm(-1, x.get(xind));

					model.addGe(exp, sigma-1, "Constr_(36)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
					conta_vincoli++;
				}
			}
		}

// add constr 37 --------------------------------------------
		for(int v=1;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici_add.stream().filter(e->e.xj.equals(dep) && e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				if(!xind.xi.contains("D")) {
					IloLinearNumExpr exp = model.linearNumExpr();
					exp.addTerm(1, r.get(xind.xi));
					exp.addTerm(-D.get(xind.xi).get(xind.xj), e.get(xind));
					exp.addTerm(-1, x.get(xind));

					model.addGe(exp, sigma-1, "Constr_(37a)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
					conta_vincoli++;
				}
			}
		}

		// update objective ********************************
		// Costo veicoli
		model.setLinearCoef(obiett,violK,0.0); // azzero il coeff della violK
		model.setLinearCoef(obiett,CostoVeicoliInitSol,0.0);

		IloLinearNumExpr obCV= model.linearNumExpr();
		obCV.addTerm(1.0,CostoVeicoli);

		for(XIndex xind:x.keySet()){
			if(xind.xi.contains("D")){
				obCV.addTerm(-FC, x.get(xind));
			}
		}
		defCostoVei = model.addEq(obCV,0.0,"CostoVe");

		IloLinearNumExpr obCostVe = model.linearNumExpr();
		obCostVe.addTerm(1.0,CostoVeicoli);
		model.addToExpr(obiett,obCostVe );

		// Costo Energia ---------------------------------
		model.setLinearCoef(obiett,CostoEnergiaInitSol,0.0);

		IloLinearNumExpr obCE= model.linearNumExpr();
		obCE.addTerm(1.0,CostoEnergia);
		for(node n:Dep)//solo una tecnologia disponibile al deposito
		{
			obCE.addTerm(-Cb*FE*RRT.get("S0").get(0).cost, r_v_0.get(n.id));
		}

		for(IJSRIndex ijsr:IJSRindici_add) {
			double costoEn = RRT.get(ijsr.staz).stream().filter(e-> e.id== ijsr.tec).findFirst().get().cost;
			obCE.addTerm(-Cb*FE*costoEn, beta.get(ijsr));
		}
		defCostoEn = model.addEq(obCE,0.0,"CostoEn");

		IloLinearNumExpr obCostEn = model.linearNumExpr();
		obCostEn.addTerm(1.0,CostoEnergia);
		model.addToExpr(obiett,obCostEn );

		// Costo drivers ---------------------------------
		model.setLinearCoef(obiett,CostoDriversInitSol,0.0);
		IloLinearNumExpr obCD= model.linearNumExpr();
		obCD.addTerm(1.0,CostoDrivers);

		for(XIndex ijs: Xindici_add){
			if(ijs.staz.equals("fictius")) {
				obCD.addTerm(-FD, t.get(ijs));
			}
			else {
				obCD.addTerm(-FD, ta.get(ijs));
				obCD.addTerm(-FD, tb.get(ijs));
				obCD.addTerm(-FD, b.get(ijs));
			}
		}

		for(int i=0;i<N.length;i++) {
			obCD.addTerm(-FD, w.get(N[i].id));
		}
		defCostoDrivers = model.addEq(obCD,0.0,"CostoDrv");

		IloLinearNumExpr obCostDrv = model.linearNumExpr();
		obCostDrv.addTerm(1.0,CostoDrivers);
		model.addToExpr(obiett,obCostDrv );

	}
*/
public void initModel(cloneless_tech2 jc) throws IloException {
		add_objective_function_Ok();
		//System.out.println("obj " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_2_();
		//System.out.println("2 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_3_();
		//System.out.println("3 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_4_();
		//System.out.println("4 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_5_();
		//System.out.println("5 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_6_();
		//System.out.println("6 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_7_();
		//System.out.println("7 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_8_();
		//System.out.println("8 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_9_();
		//System.out.println("9 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_10_();
		//System.out.println("10 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_11_();
		//System.out.println("11 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_12a_();
		//System.out.println("12a - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_12b_();
		//System.out.println("12b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_13a_();
		//System.out.println("13a - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_13b_();
		//System.out.println("13b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_14_();
		//System.out.println("14 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_15_();
		//System.out.println("15 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_16_();
		//System.out.println("16 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_17_();
		//System.out.println("17 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_18_();
		//System.out.println("18 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_19_();
		//System.out.println("19 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_20_();
		//System.out.println("20 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_21_();
		//System.out.println("21 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_22_();
		//System.out.println("22 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_23a_();
		//System.out.println("23a - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_23b_();
		//System.out.println("23b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		//commentiamo i vincoli 24 perch� doppiano i 26
		//add_constraint_24_();
		//System.out.println("24 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_25ab_();
		//System.out.println("25ab - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		//add_constraint_25b_();
		//System.out.println("25b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_26_();
		//System.out.println("26 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_27_();
		//System.out.println("27 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_28_();
		//System.out.println("28 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_29_();
		//System.out.println("29 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_30_();
		//System.out.println("30 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_31_();
		//System.out.println("31 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_32_();
		//System.out.println("32 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		//mancano i vinoli 33 perch� nella numerazione attuale sono quelli sulla natura delle variabili
		//i vincoli 34-37b sono stata aggiunti per gestire il rientro al deposito
		add_constraint_34_();
		//System.out.println("34 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_35_();
		//System.out.println("35 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_36_();
		//System.out.println("36 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		add_constraint_37a_();
		//System.out.println("37 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
		//add_constraint_37b_();
		//System.out.println("37 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);

	}

public void initModelOneVeic(cloneless_tech2 jc, int trueNumVeic) throws IloException {
	//add_objective_function();
	add_objective_function_Ok();
	//System.out.println("obj " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_2_();
	//System.out.println("2 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
 	add_constraint_3_();
	//System.out.println("3 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	//ho eliminato i vincoli 4 perch� in questo caso abbiamo solo un veicolo
	//add_constraint_4bis_(trueNumVeic);
	////System.out.println("4bis - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_5_();
	//System.out.println("5 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_6_();
	//System.out.println("6 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
 	add_constraint_7_();
	//System.out.println("7 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
  	add_constraint_8_();
	//System.out.println("8 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_9_();
	//System.out.println("9 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_10_();
	//System.out.println("10 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_11_();
	//System.out.println("11 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_12a_();
	//System.out.println("12a - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_12b_();
	//System.out.println("12b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_13a_();
	//System.out.println("13a - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_13b_();
	//System.out.println("13b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_14_();
	//System.out.println("14 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_15_();
	//System.out.println("15 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_16_();
	//System.out.println("16 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	//add_constraint_17_();
	//System.out.println("17 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	/*add_constraint_18_();
	//System.out.println("18 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_19_();
	//System.out.println("19 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_20_();
	//System.out.println("20 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_21_();
	//System.out.println("21 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_22_();
	//System.out.println("22 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_23a_();
	//System.out.println("23a - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_23b_();
	//System.out.println("23b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	//commentiamo i vincoli 24 perch� doppiano i 26
	//add_constraint_24_();
	//System.out.println("24 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_25ab_();
	//System.out.println("25ab - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	//add_constraint_25b_();
	//System.out.println("25b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_26_();
	//System.out.println("26 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_27_();
	//System.out.println("27 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	*/
	add_constraint_28_();
	//System.out.println("28 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_29_();
	//System.out.println("29 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_30_();
	//System.out.println("30 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_31_();
	//System.out.println("31 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_32_();
	//System.out.println("32 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	//mancano i vincoli 33 perch� nella numerazione attuale sono quelli sulla natura delle variabili
	//i vincoli 34-37b sono stati aggiunti per gestire il rientro al deposito
	add_constraint_34_();
	//System.out.println("34 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_35_();
	//System.out.println("35 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_36_();
	//System.out.println("36 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	add_constraint_37a_();
	//System.out.println("37 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	//add_constraint_37b_();
	//System.out.println("37 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
	
}

public void initiVariables() throws IloException {

	for(int ind=0;ind<Xindici.size();ind++) {
		if(x.containsKey(Xindici.get(ind)))
			continue;
		x.put(Xindici.get(ind),model.boolVar("x_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz) );
		conta_variabili_int++;
		b.put(Xindici.get(ind),model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"b_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz) );
		conta_variabili_int++;
		c.put(Xindici.get(ind),model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"c_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz) );
		conta_variabili_int++;
		if(Xindici.get(ind).staz.equals("fictius")){
			v.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"v_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj));
			conta_variabili_cont++;
			t.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"t_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj));
			conta_variabili_cont++;
			e.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"e_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj));
			conta_variabili_cont++;
		}
		else {
			va.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"va_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			ta.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"ta_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			ea.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"ea_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			vb.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"vb_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			tb.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"tb_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			eb.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"eb_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			
		}
		
	}
	for(int ind=0;ind<Findici.size();ind++) {
		if(f.containsKey(Findici.get(ind)))
			continue;
		f.put(Findici.get(ind), model.numVar(0, Double.POSITIVE_INFINITY, IloNumVarType.Float, "f_" + Findici.get(ind).xi + "_" + Findici.get(ind).xj));
		conta_variabili_cont++;
	}
	for(int ind=0; ind<N.length;ind++){
		if(N[ind].id.contains("C")||N[ind].id.contains("S")||N[ind].id.contains("D0"))
			continue;
		r.put(N[ind].id,model.numVar(sigma,gamma,IloNumVarType.Float,"r_"+N[ind].id));
		conta_variabili_cont++;
		dr.put(N[ind].id,model.numVar(0.0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"dr_"+N[ind].id));
		conta_variabili_cont++;
		w.put(N[ind].id,model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"w_"+N[ind].id));
		conta_variabili_cont++;
		a.put(N[ind].id,model.numVar(N[ind].s,N[ind].e,IloNumVarType.Float,"a_"+N[ind].id));
		conta_variabili_cont++;
		viola.put(N[ind].id,model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"viola_"+N[ind].id));
		conta_variabili_cont++;
	}
	r_v_0.get(Dep[0].id).setLB(0.0);
	for(int ind=1;ind<Dep.length;ind++) {
		a.put(Dep[ind].id,model.numVar(Dep[ind].s,Dep[ind].e,IloNumVarType.Float,"a_"+Dep[ind].id));
		conta_variabili_cont++;
		r_v_0.put(Dep[ind].id,model.numVar(0.0,gamma,IloNumVarType.Float,"r_v^0"+Dep[ind].id));
		conta_variabili_cont++;
	}
	for(XIndex ijs:Xindici.stream().filter(e-> !e.staz.equals("fictius")).collect(Collectors.toList())) {
		ArrayList<IloNumVar> varBetaList = new ArrayList<>();

		for(IJSRIndex ijsr:IJSRindici.stream().filter(e->e.xi.equals(ijs.xi) && e.xj.equals(ijs.xj) && e.staz.equals(ijs.staz)).collect(Collectors.toList()))
		{	if(beta.containsKey(ijsr))
				continue;
			IloNumVar betaVar = model.numVar(0, gamma - sigma, IloNumVarType.Float, "beta_" + ijsr.xi + "_" + ijsr.xj + "_" + ijsr.staz + "_" + ijsr.tec);
			varBetaList.add(betaVar);
			beta.put(ijsr, betaVar);
			conta_variabili_cont++;
		}
		if(varBetaList.size()>0) {
			double[] weights = new double[varBetaList.size()];
			for (int i = 0; i < varBetaList.size(); i++)
				weights[i] = 1.0+i;

			model.addSOS1(varBetaList.toArray(new IloNumVar[0]), weights);
		}
	}
}

public void AddVariables(int addedVehicles) throws IloException {
	int inda= Dep.length - addedVehicles;
	ArrayList<XIndex> AddXindici = new ArrayList<XIndex>();
	ArrayList<IJind> AddFindici = new ArrayList<IJind>();
	ArrayList<IJSRIndex> AddIJSRindici = new ArrayList<IJSRIndex>();

	for(XIndex ijs:Xindici.stream().filter(e-> (e.xi.equals("D0")||e.xj.equals("D0"))).collect(Collectors.toList()))
	{
		if(ijs.xi.equals("D0")){
			for(int i=inda;i<num_veic;i++)
			{
				AddXindici.add(new XIndex("D"+i,ijs.xj,ijs.staz));
				if(!Findici.contains(new IJind("D"+i,ijs.xj)))
				{
					AddFindici.add(new IJind("D"+i,ijs.xj));
					Findici.add(new IJind("D"+i,ijs.xj));
				}
				if(!IJSRindici.contains(new IJSRIndex("D"+i,ijs.xj,ijs.staz,0)))
					AddIJSRindici.add(new IJSRIndex("D"+i,ijs.xj,ijs.staz,0));
			}
		}
		if(ijs.xj.equals("D0")){
			for(int i=inda;i<num_veic;i++)
			{
				AddXindici.add(new XIndex(ijs.xi,"D"+i,ijs.staz));
				if(!Findici.contains(new IJind(ijs.xi,"D"+i)))
				{
					AddFindici.add(new IJind(ijs.xi,"D"+i));
					Findici.add(new IJind(ijs.xi,"D"+i));
				}
				if(!IJSRindici.contains(new IJSRIndex(ijs.xi,"D"+i,ijs.staz,0)))
					AddIJSRindici.add(new IJSRIndex(ijs.xi,"D"+i,ijs.staz,0));
			}
		}
	}

	for (XIndex ijs:AddXindici) {
		Xindici.add(ijs);
	}

		for(int ind=0;ind<AddXindici.size();ind++) {
			x.put(AddXindici.get(ind),model.boolVar("x_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj+"_"+AddXindici.get(ind).staz) );
			conta_variabili_int++;
			b.put(AddXindici.get(ind),model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"b_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj+"_"+AddXindici.get(ind).staz) );
			conta_variabili_int++;
			c.put(AddXindici.get(ind),model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"c_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj+"_"+AddXindici.get(ind).staz) );
			conta_variabili_int++;
			if(AddXindici.get(ind).staz.equals("fictius")){
				v.put(AddXindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"v_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj));
				conta_variabili_cont++;
				t.put(AddXindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"t_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj));
				conta_variabili_cont++;
				e.put(AddXindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"e_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj));
				conta_variabili_cont++;
			}
			else {
				va.put(AddXindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"va_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj+"_"+AddXindici.get(ind).staz));
				conta_variabili_cont++;
				ta.put(AddXindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"ta_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj+"_"+AddXindici.get(ind).staz));
				conta_variabili_cont++;
				ea.put(AddXindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"ea_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj+"_"+AddXindici.get(ind).staz));
				conta_variabili_cont++;
				vb.put(AddXindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"vb_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj+"_"+AddXindici.get(ind).staz));
				conta_variabili_cont++;
				tb.put(AddXindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"tb_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj+"_"+AddXindici.get(ind).staz));
				conta_variabili_cont++;
				eb.put(AddXindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"eb_"+AddXindici.get(ind).xi+"_"+AddXindici.get(ind).xj+"_"+AddXindici.get(ind).staz));
				conta_variabili_cont++;
			}
		}

		for(int ind=0;ind<AddFindici.size();ind++) {
			f.put(AddFindici.get(ind), model.numVar(0, Double.POSITIVE_INFINITY, IloNumVarType.Float, "f_" + AddFindici.get(ind).xi + "_" + AddFindici.get(ind).xj));
			conta_variabili_cont++;
		}

		/*
		for(int ind=0; ind<N.length;ind++){
			if(N[ind].id.contains("C")||N[ind].id.contains("S")||N[ind].id.contains("D0"))
				continue;
			r.put(N[ind].id,model.numVar(sigma,gamma,IloNumVarType.Float,"r_"+N[ind].id));
			conta_variabili_cont++;
			dr.put(N[ind].id,model.numVar(0.0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"dr_"+N[ind].id));
			conta_variabili_cont++;
			w.put(N[ind].id,model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"w_"+N[ind].id));
			conta_variabili_cont++;
			a.put(N[ind].id,model.numVar(N[ind].s,N[ind].e,IloNumVarType.Float,"a_"+N[ind].id));
			conta_variabili_cont++;
			viola.put(N[ind].id,model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"viola_"+N[ind].id));
			conta_variabili_cont++;
		}
		*/

		r_v_0.get(Dep[0].id).setLB(0.0);
		for(int ind=inda;ind<Dep.length;ind++) {
			a.put(Dep[ind].id,model.numVar(Dep[ind].s,Dep[ind].e,IloNumVarType.Float,"a_"+Dep[ind].id));
			conta_variabili_cont++;
			r_v_0.put(Dep[ind].id,model.numVar(0.0,gamma,IloNumVarType.Float,"r_v^0"+Dep[ind].id));
			conta_variabili_cont++;
		}

		for(XIndex ijs:AddXindici.stream().filter(e-> !e.staz.equals("fictius")).collect(Collectors.toList())) {
			ArrayList<IloNumVar> varBetaList = new ArrayList<>();

			for(IJSRIndex ijsr:AddIJSRindici.stream().filter(e->e.xi.equals(ijs.xi) && e.xj.equals(ijs.xj) && e.staz.equals(ijs.staz)).collect(Collectors.toList()))
			{
				IloNumVar betaVar = model.numVar(0, gamma - sigma, IloNumVarType.Float, "beta_" + ijsr.xi + "_" + ijsr.xj + "_" + ijsr.staz + "_" + ijsr.tec);
				varBetaList.add(betaVar);
				beta.put(ijsr, betaVar);
				conta_variabili_cont++;
			}
			if(varBetaList.size()>0) {
				double[] weights = new double[varBetaList.size()];
				for (int i = 0; i < varBetaList.size(); i++)
					weights[i] = 1.0+i;

				model.addSOS1(varBetaList.toArray(new IloNumVar[0]), weights);
			}
		}
	}

public void initVariablesOneVeic() throws FileNotFoundException, IloException {

model= new IloCplex();

Cplex_time_spent=model.getCplexTime();

for ( int ind=0; ind<Xindici.size();ind++) {
		x.put(Xindici.get(ind),model.boolVar("x_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz) );
		conta_variabili_int++;
		b.put(Xindici.get(ind),model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"b_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz) );
		conta_variabili_int++;
		c.put(Xindici.get(ind),model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"c_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz) );
		conta_variabili_int++;
		if(Xindici.get(ind).staz.equals("fictius")){
			v.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"v_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj));
			conta_variabili_cont++;
			t.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"t_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj));
			conta_variabili_cont++;
			e.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"e_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj));
			conta_variabili_cont++;
		}
		else {
			va.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"va_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			ta.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"ta_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			ea.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"ea_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			vb.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"vb_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			tb.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"tb_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			eb.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"eb_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
			conta_variabili_cont++;
			
		}
	}

for(int ind=0;ind<Findici.size();ind++) {
		f.put(Findici.get(ind), model.numVar(0, Double.POSITIVE_INFINITY, IloNumVarType.Float, "f_" + Findici.get(ind).xi + "_" + Findici.get(ind).xj));
		conta_variabili_cont++;
	}

for(int ind=0; ind<N.length;ind++){
	//r.put(N[ind].id,model.numVar(Double.NEGATIVE_INFINITY,gamma,IloNumVarType.Float,"r_"+N[ind].id)); **** Scommentare per LB sigma violabile
	r.put(N[ind].id,model.numVar(sigma,gamma,IloNumVarType.Float,"r_"+N[ind].id));
	conta_variabili_cont++;
	dr.put(N[ind].id,model.numVar(0.0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"dr_"+N[ind].id));
	conta_variabili_cont++;
	w.put(N[ind].id,model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"w_"+N[ind].id));
	conta_variabili_cont++;
	a.put(N[ind].id,model.numVar(N[ind].s,N[ind].e,IloNumVarType.Float,"a_"+N[ind].id));
	conta_variabili_cont++;
	viola.put(N[ind].id,model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"viola_"+N[ind].id));
	conta_variabili_cont++;
}
//new
for(int ind=0;ind<Dep.length;ind++) {
	a.put(Dep[ind].id,model.numVar(Dep[ind].s,Dep[ind].e,IloNumVarType.Float,"a_"+Dep[ind].id));
	conta_variabili_cont++;
	r_v_0.put(Dep[ind].id,model.numVar(gamma,gamma,IloNumVarType.Float,"r_v^0"+Dep[ind].id)); // LB=UB=gamma per inizializzazione
	conta_variabili_cont++;
}
//new - NON SOS1
/* for ( int ind=0; ind<IJSRindici.size();ind++) {
	beta.put(IJSRindici.get(ind), model.numVar(0,gamma-sigma,IloNumVarType.Float,"beta_"+IJSRindici.get(ind).xi+"_"+IJSRindici.get(ind).xj+"_"+IJSRindici.get(ind).staz+"_"+IJSRindici.get(ind).tec));
	conta_variabili_cont++;
}*/

// VARIABILI Beta_ijsr sono gruppi di SOS1 per ogni terna ijs fissata
	for(XIndex ijs:Xindici.stream().filter(e-> !e.staz.equals("fictius")).collect(Collectors.toList())) {
		ArrayList<IloNumVar> varBetaList = new ArrayList<>();

		for(IJSRIndex ijsr:IJSRindici.stream().filter(e->e.xi.equals(ijs.xi) && e.xj.equals(ijs.xj) && e.staz.equals(ijs.staz)).collect(Collectors.toList()))
		{
			IloNumVar betaVar = model.numVar(0, gamma - sigma, IloNumVarType.Float, "beta_" + ijsr.xi + "_" + ijsr.xj + "_" + ijsr.staz + "_" + ijsr.tec);
			varBetaList.add(betaVar);
			beta.put(ijsr, betaVar);
			conta_variabili_cont++;
		}
		if(varBetaList.size()>0) {
			double[] weights = new double[varBetaList.size()];
			for (int i = 0; i < varBetaList.size(); i++)
				weights[i] = 1.0+i;

			model.addSOS1(varBetaList.toArray(new IloNumVar[0]), weights);
		}
	}

violK = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float, "ViolK");

CostoVeicoli = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float,"CostoVeicoli");
CostoDrivers = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float,"CostoDrivers");
CostoEnergia = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float,"CostoEnergia");
CostoEnergiaInitSol = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float,"CostoEnergiaInitSol");
CostoDriversInitSol = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float,"CostoDriversInitSol");
CostoVeicoliInitSol = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float,"CostoVeicoliInitSol");

}

public void initVariables_Ok() throws FileNotFoundException, IloException {

	model= new IloCplex();
		for ( int ind=0; ind<Xindici.size();ind++) {
			x.put(Xindici.get(ind),model.boolVar("x_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz) );
			conta_variabili_int++;
			b.put(Xindici.get(ind),model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"b_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz) );
			conta_variabili_int++;
			c.put(Xindici.get(ind),model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"c_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz) );
			conta_variabili_int++;
			if(Xindici.get(ind).staz.equals("fictius")){
				v.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"v_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj));
				conta_variabili_cont++;
				t.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"t_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj));
				conta_variabili_cont++;
				e.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"e_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj));
				conta_variabili_cont++;
			}
			else {
				va.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"va_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
				conta_variabili_cont++;
				ta.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"ta_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
				conta_variabili_cont++;
				ea.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"ea_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
				conta_variabili_cont++;
				vb.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"vb_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
				conta_variabili_cont++;
				tb.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"tb_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
				conta_variabili_cont++;
				eb.put(Xindici.get(ind), model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"eb_"+Xindici.get(ind).xi+"_"+Xindici.get(ind).xj+"_"+Xindici.get(ind).staz));
				conta_variabili_cont++;

			}
		}

		for(int ind=0;ind<Findici.size();ind++) {
			f.put(Findici.get(ind), model.numVar(0, Double.POSITIVE_INFINITY, IloNumVarType.Float, "f_" + Findici.get(ind).xi + "_" + Findici.get(ind).xj));
			conta_variabili_cont++;
		}

		for(int ind=0; ind<N.length;ind++){
			//r.put(N[ind].id,model.numVar(Double.NEGATIVE_INFINITY,gamma,IloNumVarType.Float,"r_"+N[ind].id)); **** Scommentare per LB sigma violabile
			r.put(N[ind].id,model.numVar(sigma,gamma,IloNumVarType.Float,"r_"+N[ind].id));
			conta_variabili_cont++;
			dr.put(N[ind].id,model.numVar(0.0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"dr_"+N[ind].id));
			conta_variabili_cont++;
			w.put(N[ind].id,model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"w_"+N[ind].id));
			conta_variabili_cont++;
			a.put(N[ind].id,model.numVar(N[ind].s,N[ind].e,IloNumVarType.Float,"a_"+N[ind].id));
			conta_variabili_cont++;
			viola.put(N[ind].id,model.numVar(0,Double.POSITIVE_INFINITY,IloNumVarType.Float,"viola_"+N[ind].id));
			conta_variabili_cont++;
		}
//new
		for(int ind=0;ind<Dep.length;ind++) {
			a.put(Dep[ind].id,model.numVar(Dep[ind].s,Dep[ind].e,IloNumVarType.Float,"a_"+Dep[ind].id));
			conta_variabili_cont++;
			r_v_0.put(Dep[ind].id,model.numVar(0.0,gamma,IloNumVarType.Float,"r_v0"+Dep[ind].id));
			conta_variabili_cont++;
		}
//new - NON SOS1
/* for ( int ind=0; ind<IJSRindici.size();ind++) {
	beta.put(IJSRindici.get(ind), model.numVar(0,gamma-sigma,IloNumVarType.Float,"beta_"+IJSRindici.get(ind).xi+"_"+IJSRindici.get(ind).xj+"_"+IJSRindici.get(ind).staz+"_"+IJSRindici.get(ind).tec));
	conta_variabili_cont++;
}*/

// VARIABILI Beta_ijsr sono gruppi di SOS1 per ogni terna ijs fissata
		for(XIndex ijs:Xindici.stream().filter(e-> !e.staz.equals("fictius")).collect(Collectors.toList())) {
			ArrayList<IloNumVar> varBetaList = new ArrayList<>();

			for(IJSRIndex ijsr:IJSRindici.stream().filter(e->e.xi.equals(ijs.xi) && e.xj.equals(ijs.xj) && e.staz.equals(ijs.staz)).collect(Collectors.toList()))
			{
				IloNumVar betaVar = model.numVar(0, gamma - sigma, IloNumVarType.Float, "beta_" + ijsr.xi + "_" + ijsr.xj + "_" + ijsr.staz + "_" + ijsr.tec);
				varBetaList.add(betaVar);
				beta.put(ijsr, betaVar);
				conta_variabili_cont++;
			}
			if(varBetaList.size()>0) {
				double[] weights = new double[varBetaList.size()];
				for (int i = 0; i < varBetaList.size(); i++)
					weights[i] = 1.0+i;

				model.addSOS1(varBetaList.toArray(new IloNumVar[0]), weights);
			}
		}


		CostoVeicoli = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float,"CostoVeicoli");
		CostoDrivers = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float,"CostoDrivers");
		CostoEnergia = model.numVar(0.0, Double.POSITIVE_INFINITY,IloNumVarType.Float,"CostoEnergia");

	}


public void solveRelaxation(String file_model) throws IloException, FileNotFoundException {
	String name_model=file_model.replace(".txt", "");
	XConv = new HashMap<XIndex, IloConversion>(); // HashMap che contiene le conversioni applicate alle var x

	for(Map.Entry<XIndex, IloNumVar> varX: x.entrySet()){
				IloConversion conv = model.conversion(varX.getValue(), IloNumVarType.Float);
				model.add(conv);
				XConv.put(varX.getKey(), conv);
			}

	System.out.println("X var float ... risolvo rilassamento");
	model.exportModel(name_model+"_RelKS_model.lp");
	model.solve();

	writeXval("Rel");
}

public boolean solve(String file_model) throws IloException, FileNotFoundException {
	//String name_model=file_model.replace(".txt", "");

	model.setParam(IloCplex.Param.MIP.Tolerances.MIPGap,0.005);
	//model.setParam(IloCplex.Param.MIP.Limits.Solutions,1);

	model.setOut(verbCloneless);
	model.setWarning(verbCloneless);

	if(timeLimitMIPiter>0)
		model.setParam(IloCplex.Param.TimeLimit,timeLimitMIPiter);
	else {
		if(timeLimit>0)
			model.setParam(IloCplex.Param.TimeLimit, timeLimit);
	}

		double cplexTime = model.getCplexTime();

	//IloCplex.Status status = IloCplex.Status.Unknown; // ***
   //while(status != IloCplex.Status.Optimal && total_cplex_time<timeLimit) { //***
		if(!model.solve())
			return false;
		total_cplex_time = model.getCplexTime() - cplexTime;
	//status = model.getStatus(); //****
	//System.out.println((nsol++) + ";" + status + ";" +total_cplex_time+";"+ model.getObjValue() + ";" + model.getMIPRelativeGap()); //***
	//} //***
 return true;
}

public void add_constraintBucket(ArrayList<XIndex> bucket, double Z_H) throws IloException {
		
		IloLinearNumExpr exp= model.linearNumExpr();
		for(Map.Entry<XIndex,IloNumVar>varX:x.entrySet()) {
			if(bucket.contains(varX.getKey())) {
							exp.addTerm(varX.getValue(), 1);
							varX.getValue().setUB(1.0);
					}
				}
		newVinc =  model.addGe(exp,1, "Constraint_on_Bucket:");
		newVinc2=model.addLe(ob, Z_H, "Constraint_on_obj_with_UB:"+Z_H);
}

public void delete_constraint() throws IloException {
	model.delete(newVinc2);
	model.delete(newVinc);

}

public void add_objective_function() throws IloException {
	 ob= model.linearNumExpr();

// penalizzo violazioni delle TW
	/*IloLinearNumExpr obViolTW= model.linearNumExpr();
	for(int i=1;i<N.length;i++) {
		obViolTW.addTerm(-100000.0, viola.get(N[i].id));
		ob.addTerm(100000.0, viola.get(N[i].id));
		//prMod.println(100000.0+ " viola_"+N[i].id);
	}*/

// *********** Scommentare per penalizzazione r > sigma *************
	for(int i=0;i<N.length;i++) {
		ob.addTerm(1000000.0, dr.get(N[i].id));

		IloLinearNumExpr violR = model.linearNumExpr();
		violR.addTerm(1.0,dr.get(N[i].id));
		violR.addTerm(1.0,r.get(N[i].id));
		model.addGe(violR,sigma,"Viol_"+N[i].id);
	}

// Costo veicoli ---------------------------------
IloLinearNumExpr obCV= model.linearNumExpr();
obCV.addTerm(1.0,CostoVeicoliInitSol);

for(XIndex xind:x.keySet()){
	if(xind.xi.contains("D")){
		obCV.addTerm(-FC, x.get(xind));
	}
}
model.addEq(obCV,0.0,"CostoVeicInitSolution");
ob.addTerm(1.0,CostoVeicoliInitSol);

// penalizzo se si usano pi� veicoli del num_veic variabili violK
//	ob.addTerm(100000, violK);

// Costo energia ---------------------------------
IloLinearNumExpr obCEInitSol= model.linearNumExpr();
obCEInitSol.addTerm(1.0,CostoEnergiaInitSol);

for(XIndex ijs: Xindici){ //Xindici **********
	if(ijs.staz.equals("fictius")) {
		obCEInitSol.addTerm(-Cb * FE * D.get(ijs.xi).get(ijs.xj), e.get(ijs));
	}
	else {
		obCEInitSol.addTerm(-Cb * FE * D.get(ijs.xi).get(ijs.staz), ea.get(ijs));
		obCEInitSol.addTerm(-Cb * FE * D.get(ijs.staz).get(ijs.xj), eb.get(ijs));
	}
}
model.addEq(obCEInitSol,0.0,"CostoEnInitSolution");
ob.addTerm(1.0,CostoEnergiaInitSol);
/*
	IloLinearNumExpr obCE= model.linearNumExpr();
	obCE.addTerm(1.0,CostoEnergia);
for(node n:Dep)//solo una tecnologia disponibile al deposito
{	
	obCE.addTerm(-Cb*FE*RRT.get("S0").get(0).cost, r_v_0.get(n.id));
	//ob.addTerm(Cb*FE*RRT.get("S0").get(0).cost, r_v_0.get(n.id));
}

for(IJSRIndex ijsr:IJSRindici) {
	double costoEn = RRT.get(ijsr.staz).stream().filter(e-> e.id== ijsr.tec).findFirst().get().cost;
	//for(RechargeTypes rtt:RRT.get(ijsr.staz)) {
		obCE.addTerm(-Cb*FE*costoEn, beta.get(ijsr));
		//ob.addTerm(Cb*FE*costoEn, beta.get(ijsr));
	//}
}
defCostoEn = model.addEq(obCE,0.0,"CostoEn");
ob.addTerm(0.0,CostoEnergia); */

// Costo drivers ---------------------------------
IloLinearNumExpr obCD= model.linearNumExpr();
obCD.addTerm(1.0,CostoDriversInitSol);

	for(XIndex ijs: Xindici){ // Xindici *********
		if(ijs.staz.equals("fictius")) {
			obCD.addTerm(-FD, t.get(ijs));
			//ob.addTerm(FD, t.get(ijs));
		}
		else {
			obCD.addTerm(-FD, ta.get(ijs));
			//ob.addTerm(FD, ta.get(ijs));
			obCD.addTerm(-FD, tb.get(ijs));
			//ob.addTerm(FD, tb.get(ijs));
			obCD.addTerm(-FD, b.get(ijs));
			//ob.addTerm(FD, b.get(ijs));
		}
	}

for(int i=0;i<N.length;i++) {
	obCD.addTerm(-FD, w.get(N[i].id));
	//ob.addTerm(FD, w.get(N[i].id));
	//prMod.println(FD+" W_"+N[i].id);
}
model.addEq(obCD,0.0,"CostoDrivInitSolution");
ob.addTerm(1.0,CostoDriversInitSol);

obiett = model.addObjective(IloObjectiveSense.Minimize, ob);

}

public void add_objective_function_Ok() throws IloException {
		ob= model.linearNumExpr();

// penalizzo violazioni delle TW
	/*IloLinearNumExpr obViolTW= model.linearNumExpr();
	for(int i=1;i<N.length;i++) {
		obViolTW.addTerm(-100000.0, viola.get(N[i].id));
		ob.addTerm(100000.0, viola.get(N[i].id));
		//prMod.println(100000.0+ " viola_"+N[i].id);
	}*/

// *********** Scommentare per penalizzazione r > sigma *************
		/*for(int i=0;i<N.length;i++) {
			ob.addTerm(1000000.0, dr.get(N[i].id));

			IloLinearNumExpr violR = model.linearNumExpr();
			violR.addTerm(1.0,dr.get(N[i].id));
			violR.addTerm(1.0,r.get(N[i].id));
			model.addGe(violR,sigma,"Viol_"+N[i].id);
		}*/

// Costo veicoli ---------------------------------
		IloLinearNumExpr obCV= model.linearNumExpr();
		obCV.addTerm(1.0,CostoVeicoli);

		for(XIndex xind:x.keySet()){
			if(xind.xi.contains("D")){
				obCV.addTerm(-FC, x.get(xind));
			}
		}
		model.addEq(obCV,0.0,"CostoVeic");
		ob.addTerm(1.0,CostoVeicoli);

// Costo energia ---------------------------------
	IloLinearNumExpr obCE= model.linearNumExpr();
	obCE.addTerm(1.0,CostoEnergia);
for(node n:Dep)//solo una tecnologia disponibile al deposito
{
	obCE.addTerm(-Cb*FE*RRT.get("S0").get(0).cost, r_v_0.get(n.id));
	//ob.addTerm(Cb*FE*RRT.get("S0").get(0).cost, r_v_0.get(n.id));
}

for(IJSRIndex ijsr:IJSRindici) {
	double costoEn = RRT.get(ijsr.staz).stream().filter(e-> e.id== ijsr.tec).findFirst().get().cost;
	//for(RechargeTypes rtt:RRT.get(ijsr.staz)) {
		obCE.addTerm(-Cb*FE*costoEn, beta.get(ijsr));
		//ob.addTerm(Cb*FE*costoEn, beta.get(ijsr));
	//}
}
defCostoEn = model.addEq(obCE,0.0,"CostoEn");
ob.addTerm(1.0,CostoEnergia);

// Costo drivers ---------------------------------
		IloLinearNumExpr obCD= model.linearNumExpr();
		obCD.addTerm(1.0,CostoDrivers);

		for(XIndex ijs: Xindici){
			if(ijs.staz.equals("fictius")) {
				obCD.addTerm(-FD, t.get(ijs));
				//ob.addTerm(FD, t.get(ijs));
			}
			else {
				obCD.addTerm(-FD, ta.get(ijs));
				//ob.addTerm(FD, ta.get(ijs));
				obCD.addTerm(-FD, tb.get(ijs));
				//ob.addTerm(FD, tb.get(ijs));
				obCD.addTerm(-FD, b.get(ijs));
				//ob.addTerm(FD, b.get(ijs));
			}
		}

		for(int i=0;i<N.length;i++) {
			obCD.addTerm(-FD, w.get(N[i].id));
			//ob.addTerm(FD, w.get(N[i].id));
			//prMod.println(FD+" W_"+N[i].id);
		}
		model.addEq(obCD,0.0,"CostoDriv");
		ob.addTerm(1.0,CostoDrivers);

		obiett = model.addObjective(IloObjectiveSense.Minimize, ob);

	}

public void add_constraint_m() throws IloException {

		IloLinearNumExpr exp= model.linearNumExpr();
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet())
			if(varX.getKey().xi.equals("D0")){
				exp.addTerm(1, varX.getValue());
			}
		model.addLe(exp,num_veic, "Constr_on_m");
	}

	public void add_constraint_2_() throws IloException {

		Constr2Range = new IloRange[N.length];

		for(int j=0;j<N.length;j++) {
			IloLinearNumExpr exp= model.linearNumExpr();
			String nodoj = N[j].id;
			for (XIndex xind:Xindici.stream().filter(e-> e.xj.equals(nodoj)).collect(Collectors.toList()))
			{
				exp.addTerm(1,x.get(xind));
			}
			Constr2Range[j] = model.addEq(exp, 1, "Constr_(2)_for_customer_"+N[j].id);
			conta_vincoli++;
		}
	}

	public void add_constraint_3_() throws IloException {

		Constr3Range = new IloRange[N.length];

		for(int i=0;i<N.length;i++) {
			IloLinearNumExpr exp= model.linearNumExpr();
			String nodoi = N[i].id;
			for (XIndex xind:Xindici.stream().filter(e-> e.xi.equals(nodoi)).collect(Collectors.toList()))
			{
				exp.addTerm(1,x.get(xind));

			}
			Constr3Range[i] = model.addEq(exp, 1, "Constr_(3)_for_customer_"+N[i].id);
			conta_vincoli++;
		}
	}

	public void add_constraint_4_() throws IloException {
		for (int i=0;i<Dep.length;i++) {
			IloLinearNumExpr exp= model.linearNumExpr();
			String dep = Dep[i].id;
			for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep)).collect(Collectors.toList()))
			{
				exp.addTerm(1,x.get(xind));
			}
			model.addLe(exp, 1, "Constr_(4)_for_depot_"+Dep[i].id);
			conta_vincoli++;
		}
	}

	public void add_constraint_4bis_(int trueNumVeic) throws IloException {
		IloLinearNumExpr exp= model.linearNumExpr();
		for (int i=0;i<Dep.length;i++) {
			String dep = Dep[i].id;
			for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep)).collect(Collectors.toList()))
			{
				exp.addTerm(1,x.get(xind));
			}
			exp.addTerm(-1.0, violK);
			constr4bis = model.addLe(exp, trueNumVeic, "Constr_4bis_for_depot_"+Dep[i].id);
			conta_vincoli++;
		}
	}


	public void add_constraint_5_() throws IloException {

		for (int i=0;i<Dep.length;i++) {
			IloLinearNumExpr exp= model.linearNumExpr();
			String dep = Dep[i].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep)).collect(Collectors.toList()))
			{
				exp.addTerm(1,x.get(xind));
			}
			for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep)).collect(Collectors.toList()))
			{
				exp.addTerm(-1,x.get(xind));
			}
			model.addEq(exp, 0, "Constr_(5)_for_depot_"+Dep[i].id);
			conta_vincoli++;
		}
	}

	public void add_constraint_6_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			for (int p = 0; p < P.length; p++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, t.get(xind));
				exp.addTerm(-60 * D.get(xind.xi).get(xind.xj) * kp[p], v.get(xind));
				double bigM = 60 * D.get(xind.xi).get(xind.xj) * (Math.max(kp[0]*v_min.get(new IJind(xind.xi,xind.xj)),0))+
						60 * D.get(xind.xi).get(xind.xj)*bp[0];
				exp.addTerm(-bigM, x.get(xind));

				model.addGe(exp, 60 * D.get(xind.xi).get(xind.xj) * bp[p]-bigM,"Constr_(6)_for_pair_" + xind.xi + "_" + xind.xj+"_"+p);
				conta_vincoli++;
			}
		}
	}


	public void add_constraint_7_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			for (int p = 0; p < P.length; p++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, ta.get(xind));
				exp.addTerm(-60 * D.get(xind.xi).get(xind.staz) * kp[p], va.get(xind));
				double bigM = 60 * D.get(xind.xi).get(xind.staz) * (Math.max(kp[0]*v_min.get(new IJind(xind.xi,xind.staz)),0))+
						60 * D.get(xind.xi).get(xind.staz)*bp[0];
				exp.addTerm(-bigM, x.get(xind));

				model.addGe(exp, 60 * D.get(xind.xi).get(xind.staz) * bp[p] -bigM,
						"Constr_(7)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz + "_" + p);
				conta_vincoli++;
			}
		}
	}

	public void add_constraint_8_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			for (int p = 0; p < P.length; p++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp = model.linearNumExpr();
				exp.addTerm(1, tb.get(xind));
				exp.addTerm(-60 * D.get(xind.staz).get(xind.xj) * kp[p], vb.get(xind));
				double bigM = 60 * D.get(xind.staz).get(xind.xj) * (Math.max(kp[0]*v_min.get(new IJind(xind.staz,xind.xj)),0))+
						60 * D.get(xind.staz).get(xind.xj)*bp[0];

				exp.addTerm(-bigM, x.get(xind));

				model.addGe(exp,60 * D.get(xind.staz).get(xind.xj) * bp[p]	-bigM,
						"Constr_(8)_for_pairB_" + xind.xi + "_" + xind.xj+"_station_"+xind.staz+"_"+p);
				conta_vincoli++;
			}
		}
	}


	public void add_constraint_9_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1 = model.linearNumExpr();

			exp.addTerm(1, b.get(xind));
			exp1.addTerm(1, b.get(xind));

			exp.addTerm(-1/speed_min, x.get(xind));
			exp1.addTerm(1/speed_min, x.get(xind));

			ArrayList<RechargeTypes> tecStaz = RRT.get(xind.staz);

			for (RechargeTypes rtyStaz: tecStaz){
				IJSRIndex ijsr = new IJSRIndex(xind.xi,xind.xj,xind.staz, rtyStaz.id);
				if(IJSRindici.contains(ijsr))
				{
					exp.addTerm(-1 / rtyStaz.speed, beta.get(ijsr));
					exp1.addTerm(-1 / rtyStaz.speed, beta.get(ijsr));
				}
			}
			model.addGe(exp, -1/speed_min, "Constr_(9a)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
			model.addLe(exp1, 1/speed_min, "Constr_(9b)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
			conta_vincoli += 2;
		}
	}

	public void add_constraint_10_() throws IloException {
		for (int i=0;i<Dep.length;i++) {
			String dep = Dep[i].id;

			for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius") && e.xi.equals(dep)).collect(Collectors.toList()))
			//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
			//	if (varX.getKey().staz.equals("fictius")&&varX.getKey().xi.contains("D"))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, a.get(xind.xj));
				exp.addTerm(-1, t.get(xind));
				double bigM = (NodesMap.get("D0").s + 60*D.get("D0").get(xind.xj))/v_min.get(new IJind(xind.xi,xind.xj));
				exp.addTerm(-bigM, x.get(xind));
				model.addGe(exp, -bigM, "Constr_(10)_for_pair_" + xind.xi+"_"+xind.xj);
				conta_vincoli++;
			}
		}
	}

	public void add_constraint_11_() throws IloException {
		for (int i=0;i<Dep.length;i++) {
			String dep = Dep[i].id;

			for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && e.xi.equals(dep)).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, a.get(xind.xj));
				exp.addTerm(-1, ta.get(xind));
				exp.addTerm(-1, tb.get(xind));
				exp.addTerm(-1, b.get(xind));
				double bigM = (NodesMap.get(xind.xi).s + 60*D.get(xind.xi).get(xind.staz)/v_min.get(new IJind(xind.xi,xind.staz)) +
						60*D.get(xind.staz).get(xind.xj)/v_min.get(new IJind(xind.staz,xind.xj)) + 1/speed_min);

				exp.addTerm(-bigM, x.get(xind));

				model.addGe(exp, -bigM, "Constr_(11)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
				conta_vincoli++;
			}
		}
	}

	public void add_constraint_12a_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius") && !e.xi.contains("D")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, a.get(xind.xj));
			exp.addTerm(-1, a.get(xind.xi));
			exp.addTerm(-1, t.get(xind));
			exp.addTerm(-1, w.get(xind.xi));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(-bigM, x.get(xind));

			model.addGe(exp, NodesMap.get(xind.xi).service_time -bigM, "Constr_(12a)_for_pair_"+xind.xi+"_"+xind.xj);
			conta_vincoli++;
		}
	}

	public void add_constraint_12b_() throws IloException {
		for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius") && !e.xi.contains("D")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, a.get(xind.xj));
			exp.addTerm(-1, a.get(xind.xi));
			exp.addTerm(-1, t.get(xind));
			exp.addTerm(-1, w.get(xind.xi));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(bigM, x.get(xind));

			model.addLe(exp,NodesMap.get(xind.xi).service_time+bigM, "Constr_(12b)_for_pair_"+xind.xi+"_"+xind.xj);
			conta_vincoli++;
		}
	}

	public void add_constraint_13a_() throws IloException {
		for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && !e.xi.contains("D")).collect(Collectors.toList()))
		//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		//	if (!varX.getKey().xi.contains("D")  && !varX.getKey().staz.equals("fictius"))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, a.get(xind.xj));
			exp.addTerm(-1, a.get(xind.xi));
			exp.addTerm(-1, ta.get(xind));
			exp.addTerm(-1, tb.get(xind));
			exp.addTerm(-1, w.get(xind.xi));
			exp.addTerm(-1, b.get(xind));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(-bigM, x.get(xind));

			model.addGe(exp, NodesMap.get(xind.xi).service_time - bigM, "Constr_(13a)_for_pair_" + xind.xi + "_" + xind.xj + "_station_" + xind.staz);
			conta_vincoli++;
		}
	}

	public void add_constraint_13b_() throws IloException {
		for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius") && !e.xi.contains("D")).collect(Collectors.toList()))

		//for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		//	if(!varX.getKey().xi.contains("D")  && !varX.getKey().staz.equals("fictius"))
		{
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, a.get(xind.xj));
			exp.addTerm(-1, a.get(xind.xi));
			exp.addTerm(-1, ta.get(xind));
			exp.addTerm(-1, tb.get(xind));
			exp.addTerm(-1, w.get(xind.xi));
			exp.addTerm(-1, b.get(xind));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(bigM, x.get(xind));

			model.addLe(exp,NodesMap.get(xind.xi).service_time+bigM, "Constr_(13b)_for_pair_"+xind.xi+"_"+xind.xj+"_station_"+xind.staz);
			conta_vincoli++;
		}
	}

	public void add_constraint_14_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			for(int q=0;q<Q.length;q++) {
				IloLinearNumExpr exp= model.linearNumExpr();
				IJind ij = new IJind(xind.xi, xind.xj);
				exp.addTerm(1, e.get(xind));
				exp.addTerm(-kq[q], v.get(xind));
				exp.addTerm(-phi, f.get(ij));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, bq[q]-1, "Constr_(14)_for_pair_"+ij.xi+"_"+ij.xj);
				conta_vincoli++;
			}
		}
	}

	public void add_constraint_15_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			for (int q = 0; q < Q.length; q++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				IJind ij = new IJind(xind.xi, xind.xj);
				exp.addTerm(1, ea.get(xind));
				exp.addTerm(-kq[q], va.get(xind));
				exp.addTerm(-phi, f.get(ij));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, bq[q] - 1, "Constr_(15)_for_pair_" + ij.xi + "_" + ij.xj + "_station_" + xind.staz);
				conta_vincoli++;
			}
		}
	}

	public void add_constraint_16_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			for (int q = 0; q < Q.length; q++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				IJind ij = new IJind(xind.xi, xind.xj);
				exp.addTerm(1, eb.get(xind));
				exp.addTerm(-kq[q], vb.get(xind));
				exp.addTerm(-phi, f.get(ij));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, bq[q] - 1, "Constr_(16)_for_pair_" + ij.xi + "_" + ij.xj + "_station_" + xind.staz);
				conta_vincoli++;
			}
		}
	}

	public void add_constraint_17_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1 = model.linearNumExpr();
			exp.addTerm(1, r_v_0.get(Dep[v].id));
			exp1.addTerm(1, r_v_0.get(Dep[v].id));

			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep)).collect(Collectors.toList()))
			//for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
			//	if(varX.getKey().xi.equals(Dep[v].id))
			{
				//		exp.addTerm(-sigma, varX.getValue());
				exp.addTerm(-sigma, x.get(xind));
				exp1.addTerm(-gamma, x.get(xind));
			}

			model.addGe(exp, 0, "Constr_(17a)_for_depot_" +Dep[v].id );
			model.addLe(exp1, 0, "Constr_(17b)_for_depot_" +Dep[v].id );
			conta_vincoli +=2;

			//for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
			//	if(varX.getKey().xi.equals(Dep[v].id)) {
			//		exp.addTerm(-gamma, varX.getValue());
			//	}
			//}
		}
	}

	public void add_constraint_18_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r.get(xind.xj));
				exp.addTerm(-1, r_v_0.get(xind.xi));
				exp.addTerm(D.get(xind.xi).get(xind.xj), e.get(xind));
				exp.addTerm(1, x.get(xind)); // -1
				model.addLe(exp, 1, "Constr_(18)_for_pair_" +xind.xi+"_"+xind.xj ); //Ge -1
				conta_vincoli++;
			}
		}
	}

	public void add_constraint_19_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r_v_0.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, sigma - 1, "Constr_(19)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz); // mancava sigma
				conta_vincoli++;
			}
		}
	}

	public void add_constraint_20_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				IloLinearNumExpr exp1 = model.linearNumExpr();

				exp.addTerm(1, r.get(xind.xj));
				exp.addTerm(-1, r_v_0.get(xind.xi));
				exp.addTerm(D.get(xind.xi).get(xind.staz), ea.get(xind));
				exp.addTerm(D.get(xind.staz).get(xind.xj), eb.get(xind));
				exp.addTerm(1, x.get(xind));

				exp1.addTerm(1, r.get(xind.xj));
				exp1.addTerm(-1, r_v_0.get(xind.xi));
				exp1.addTerm(D.get(xind.xi).get(xind.staz), ea.get(xind));
				exp1.addTerm(D.get(xind.staz).get(xind.xj), eb.get(xind));
				exp1.addTerm(-1, x.get(xind));

				for (RechargeTypes rtyStaz: RRT.get(xind.staz)) {
					IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rtyStaz.id);
					if (IJSRindici.contains(ijsr)) {
						exp.addTerm(-1, beta.get(ijsr));
						exp1.addTerm(-1, beta.get(ijsr));
					}
				}
				//	}
				model.addLe(exp, 1, "Constr_(20a)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				model.addGe(exp1, -1, "Constr_(20b)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli +=2;
			}
		}
	}

	public void add_constraint_21_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r_v_0.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
				//exp.addTerm(-D.get(xind.staz).get(xind.xj), eb.get(xind));
				exp.addTerm(-1, x.get(xind));
				model.addGe(exp, sigma-1, "Constr_(21)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli++;
			}
		}
	}


	public void add_constraint_22_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xi.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r_v_0.get(xind.xi));
				exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
				exp.addTerm(1, x.get(xind));

				for(RechargeTypes rt:RRT.get(xind.staz)) {
					IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
					if (IJSRindici.contains(ijsr)) {
						exp.addTerm(1, beta.get(ijsr));
					}
				}
				model.addLe(exp, gamma+1, "Constr_(22)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
				conta_vincoli++;
			}
		}
	}

	public void add_constraint_23a_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.xi.contains("D") && !e.xj.contains("D") && e.staz.equals("fictius")).collect(Collectors.toList()))
		//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		//	if (!varX.getKey().xi.contains("D") && !varX.getKey().xj.contains("D")&& varX.getKey().staz.equals("fictius"))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(xind.xj));
			exp.addTerm(-1, r.get(xind.xi));
			exp.addTerm(D.get(xind.xi).get(xind.xj), e.get(xind));
			exp.addTerm(1, x.get(xind));
			model.addLe(exp, 1, "Constr_(23a)_for_pair_" + xind.xi + "_" + xind.xj);
			conta_vincoli++;
		}
	}

	public void add_constraint_23b_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.xi.contains("D") && !e.xj.contains("D") && e.staz.equals("fictius")).collect(Collectors.toList()))
		//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		//	if (!varX.getKey().xi.contains("D") && !varX.getKey().xj.contains("D")&& varX.getKey().staz.equals("fictius"))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(xind.xj));
			exp.addTerm(-1, r.get(xind.xi));
			exp.addTerm(D.get(xind.xi).get(xind.xj), e.get(xind));
			exp.addTerm(-1, x.get(xind));
			model.addGe(exp, -1, "Constr_(23b)_for_pair_" + xind.xi + "_" + xind.xj);
			conta_vincoli++;
		}
	}

	public void add_constraint_24_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.xi.contains("D") && !e.staz.equals("fictius")).collect(Collectors.toList()))
		//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		//if (!varX.getKey().xi.contains("D") && !varX.getKey().staz.equals("fictius"))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(xind.xi));
			exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
			exp.addTerm(-1, x.get(xind));
			model.addGe(exp, sigma-1, "Constr_(24)_for_pair_"+ xind.xi+"_"+xind.xj+"_station_"+xind.staz);
			conta_vincoli++;
		}
	}

	public void add_constraint_25ab_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.xi.contains("D") && !e.xj.contains("D") && !e.staz.equals("fictius")).collect(Collectors.toList()))
		//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		//if (!varX.getKey().xi.contains("D") && !varX.getKey().xj.contains("D")&&!varX.getKey().staz.equals("fictius"))
		{
			//System.err.println(varX.getKey().xi+"------>"+varX.getKey().xj);
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1 = model.linearNumExpr();

			exp.addTerm(1, r.get(xind.xj));
			exp.addTerm(-1, r.get(xind.xi));
			exp.addTerm(D.get(xind.xi).get(xind.staz), ea.get(xind));
			exp.addTerm(D.get(xind.staz).get(xind.xj), eb.get(xind));
			exp.addTerm(1, x.get(xind));

			exp1.addTerm(1, r.get(xind.xj));
			exp1.addTerm(-1, r.get(xind.xi));
			exp1.addTerm(D.get(xind.xi).get(xind.staz), ea.get(xind));
			exp1.addTerm(D.get(xind.staz).get(xind.xj), eb.get(xind));
			exp1.addTerm(-1, x.get(xind));

			for(RechargeTypes rt:RRT.get(xind.staz)) {
				IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
				if (IJSRindici.contains(ijsr)) {

					//for(RechargeTypes rt:RRT.get(xind.staz))
					//	for(IJSRIndex ijsr:IJSRindici) {
					//		if(ijsr.xi.equals(xind.xi)&&ijsr.xj.equals(xind.xj)&&ijsr.staz.equals(xind.staz)&&
					//				ijsr.tec==rt.id) {
					exp.addTerm(-1, beta.get(ijsr));
					exp1.addTerm(-1, beta.get(ijsr));
				}
			}
			//System.err.println(exp);
			model.addLe(exp, 1, "Constr_(25a)_for_pair_"+ xind.xi+"_"+xind.xj+"_station_"+xind.staz);
			model.addGe(exp1, -1, "Constr_(25b)_for_pair_"+ xind.xi+"_"+xind.xj+"_station_"+xind.staz);

			conta_vincoli += 2;
		}
	}
/*
public void add_constraint_25b_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().xi.contains("D") && !varX.getKey().xj.contains("D")&&!varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(varX.getKey().xj));
			exp.addTerm(-1, r.get(varX.getKey().xi));
			exp.addTerm(D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
			exp.addTerm(D.get(varX.getKey().staz).get(varX.getKey().xj), eb.get(varX.getKey()));
			exp.addTerm(-1, varX.getValue());
			for(RechargeTypes rt:RRT.get(varX.getKey().staz))
				for(IJSRIndex ijsr:IJSRindici) {
					if(ijsr.xi.equals(varX.getKey().xi)&&ijsr.xj.equals(varX.getKey().xj)&&ijsr.staz.equals(varX.getKey().staz)&&
							ijsr.tec==rt.id) {
						exp.addTerm(-1, beta.get(ijsr));
					}
				}
			model.addGe(exp, -1, "Constr_(25b)_for_pair_"+ varX.getKey().xi+"_"+varX.getKey().xj+"_station_"+varX.getKey().staz);
			conta_vincoli++;
		}
	}
}*/

	public void add_constraint_26_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.xi.contains("D") && !e.staz.equals("fictius")).collect(Collectors.toList()))
		//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		//	if (!varX.getKey().xi.contains("D") && !varX.getKey().staz.equals("fictius"))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(xind.xi));
			exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
//			exp.addTerm(-D.get(xind.staz).get(xind.xj), eb.get(xind));
			exp.addTerm(-1, x.get(xind));
			model.addGe(exp, sigma-1, "Constr_(26)_for_pair_"+ xind.xi+"_"+xind.xj+"_station_"+xind.staz);
			conta_vincoli++;
		}
	}

	public void add_constraint_27_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e->!e.xi.contains("D") && !e.staz.equals("fictius")).collect(Collectors.toList()))
		//for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		//	if (!varX.getKey().xi.contains("D") && !varX.getKey().staz.equals("fictius"))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(xind.xi));
			exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
			//exp.addTerm(-D.get(xind.staz).get(xind.xj), eb.get(xind));

			for(RechargeTypes rt:RRT.get(xind.staz)) {
				IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
				if (IJSRindici.contains(ijsr)) {

					//	for(IJSRIndex ijsr:IJSRindici) {
					//		if(ijsr.xi.equals(xind.xi)&&ijsr.xj.equals(xind.xj)&&ijsr.staz.equals(xind.staz)&&
					//				ijsr.tec==rt.id) {
					exp.addTerm(1, beta.get(ijsr));
				}
			}
			exp.addTerm(1, x.get(xind));
			model.addLe(exp, gamma+1, "Constr_(27)_for_pair_"+ xind.xi+"_"+xind.xj+"_station_"+xind.staz);
			conta_vincoli++;
		}
	}

	public void add_constraint_28_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e-> e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			IJind ij = new IJind(xind.xi, xind.xj);
			exp.addTerm(1, v.get(xind));
			exp.addTerm(-v_max.get(ij), x.get(xind));
			model.addLe(exp, 0, "Constr_(28a)_for_pair_" + ij.xi + "_" + ij.xj);
			IloLinearNumExpr exp1 = model.linearNumExpr();
			exp1.addTerm(1, v.get(xind));
			exp1.addTerm(-v_min.get(ij), x.get(xind));
			model.addGe(exp1, 0, "Constr_(28b)_for_pair_" + ij.xi + "_" + ij.xj);
			conta_vincoli += 2;
		}
	}

	public void add_constraint_29_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e-> !e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1= model.linearNumExpr();
			exp.addTerm(1, va.get(xind));
			exp1.addTerm(1, va.get(xind));
			exp.addTerm(-v_max.get(new IJind(xind.xi,xind.staz)), x.get(xind));
			exp1.addTerm(-v_min.get(new IJind(xind.xi,xind.staz)), x.get(xind));
			model.addLe(exp, 0, "Constr_(29)_for_customer_"+ xind.xi+"_station_"+ xind.staz);
			model.addGe(exp1, 0, "Constr_(29)_for_customer_"+ xind.xi+"_station_"+ xind.staz);
			conta_vincoli += 2;
		}
	}

	public void add_constraint_30_() throws IloException {

		for(XIndex xind: Xindici.stream().filter(e-> !e.staz.equals("fictius")).collect(Collectors.toList()))
		{
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1= model.linearNumExpr();
			exp.addTerm(1, vb.get(xind));
			exp1.addTerm(1, vb.get(xind));
			exp.addTerm(-v_max.get(new IJind(xind.staz,xind.xj)), x.get(xind));
			exp1.addTerm(-v_min.get(new IJind(xind.staz,xind.xj)), x.get(xind));
			model.addLe(exp, 0, "Constr_(30)_for_customer_"+ xind.staz+"_station_"+ xind.xj);
			model.addGe(exp1, 0, "Constr_(30)_for_customer_"+ xind.staz+"_station_"+ xind.xj);
			conta_vincoli += 2;
		}
	}

	public void add_constraint_31_() throws IloException {

		Constr31Range = new IloRange[N.length];
		for(int i=1;i<N.length;i++) {
			String nodoCust = N[i].id;

			IloLinearNumExpr exp= model.linearNumExpr();
			for(IJind find: Findici.stream().filter(e-> e.xj.equals(nodoCust)).collect(Collectors.toList())) {
				exp.addTerm(1, f.get(find));
			}

			for(IJind find: Findici.stream().filter(e-> e.xi.equals(nodoCust)).collect(Collectors.toList())) {
				exp.addTerm(-1, f.get(find));
			}
			Constr31Range[i] = model.addEq(exp, N[i].demand, "Constr_(31)_for_customer_"+N[i].id);
			conta_vincoli++;
		}
	}

	public void add_constraint_32_() throws IloException {
		for(Map.Entry<IJind,IloNumVar> varF: f.entrySet()){
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, f.get(varF.getKey()));

			for (XIndex xKey : Xindici.stream().filter(e->e.xi.equals(varF.getKey().xi) && e.xj.equals(varF.getKey().xj)).collect(Collectors.toList())) {
				exp.addTerm(-Cl, x.get(xKey));
			}
			model.addLe(exp, 0, "Constr_(32)_for_pair_"+varF.getKey().xi+"_"+varF.getKey().xj);
			conta_vincoli++;
		}
	}



	public void add_constraint_34_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				if(!xind.xi.contains("D")) {
					IloLinearNumExpr exp = model.linearNumExpr();
					exp.addTerm(1, r.get(xind.xi));
					exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
					exp.addTerm(-1, x.get(xind));

					model.addGe(exp, sigma-1, "Constr_(34)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
					conta_vincoli++;
				}
			}
		}
	}
	public void add_constraint_35_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				if(!xind.xi.contains("D")) {
					IloLinearNumExpr exp = model.linearNumExpr();
					exp.addTerm(1, r.get(xind.xi));
					exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
					for(RechargeTypes rt:RRT.get(xind.staz)) {
						IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
						if (IJSRindici.contains(ijsr)) {
							exp.addTerm(1, beta.get(ijsr));
						}
					}
					exp.addTerm(1, x.get(xind));

					model.addLe(exp, gamma+1, "Constr_(35)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
					conta_vincoli++;
				}
			}
		}
	}
	public void add_constraint_36_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep) && !e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				if(!xind.xi.contains("D")) {
					IloLinearNumExpr exp = model.linearNumExpr();
					exp.addTerm(1, r.get(xind.xi));
					exp.addTerm(-D.get(xind.xi).get(xind.staz), ea.get(xind));
					for(RechargeTypes rt:RRT.get(xind.staz)) {
						IJSRIndex ijsr = new IJSRIndex(xind.xi, xind.xj, xind.staz, rt.id);
						if (IJSRindici.contains(ijsr)) {
							exp.addTerm(1, beta.get(ijsr));
						}
					}
					exp.addTerm(-D.get(xind.staz).get(xind.xj), eb.get(xind));
					exp.addTerm(-1, x.get(xind));

					model.addGe(exp, sigma-1, "Constr_(36)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
					conta_vincoli++;
				}
			}
		}
	}
	public void add_constraint_37a_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep) && e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				if(!xind.xi.contains("D")) {
					IloLinearNumExpr exp = model.linearNumExpr();
					exp.addTerm(1, r.get(xind.xi));
					exp.addTerm(-D.get(xind.xi).get(xind.xj), e.get(xind));
					exp.addTerm(-1, x.get(xind));

					model.addGe(exp, sigma-1, "Constr_(37a)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
					conta_vincoli++;
				}
			}
		}
	}

	public void add_constraint_37b_() throws IloException {
		for(int v=0;v<Dep.length;v++) {
			String dep = Dep[v].id;

			for(XIndex xind: Xindici.stream().filter(e->e.xj.equals(dep) && e.staz.equals("fictius")).collect(Collectors.toList()))
			{
				if(!xind.xi.contains("D")) {
					IloLinearNumExpr exp = model.linearNumExpr();
					exp.addTerm(1, r.get(xind.xi));
					exp.addTerm(-D.get(xind.xi).get(xind.xj), e.get(xind));
					exp.addTerm(1, x.get(xind));

					model.addLe(exp, gamma+1, "Constr_(37b)_for_" +xind.xi+"_"+xind.xj+"_"+xind.staz);
					conta_vincoli++;
				}
			}
		}
	}

public void generate_nodes(InstanceReaderGeneratorTech ir) {

	N=new node[ir.numNodes-1];
	RS=new node[ir.NumRS];
	int j=0;
	int k=0;
	int j0=0;
	Dep=new node[num_veic];
	N0=new node[N.length+Dep.length];
	//prima carico in N0 tutti i clienti e tutte le stazioni
	for(int i=0; i<ir.NodeId.size();i++)
	{	if(ir.NodeId.get(i).contains("C")) {
		 node n=new node();
		 n.id=ir.NodeId.get(i);
		 n.type=ir.Type.get(i);
		 n.demand=ir.Demand.get(i);
		 n.s=ir.ReadyTime.get(i);
		 n.e=ir.DueDate.get(i);
		 n.service_time=ir.ServiceTime.get(i);
		 n.x=ir.XCoord.get(i);
		 n.y=ir.YCoord.get(i);
		 N[j]=n;
		 N0[j0]=n;
		 NodesMap.put(n.id, n);
		 NodeSImap.put(n.id,j0);
		 j++;
		 j0++;
	 }
	else {
		if(ir.NodeId.get(i).contains("S")) {
		node n=new node();
		 n.id=ir.NodeId.get(i);
		 n.type=ir.Type.get(i);
		 n.demand=ir.Demand.get(i);
		 n.s=ir.ReadyTime.get(i);
		 n.e=ir.DueDate.get(i);
		 n.service_time=ir.ServiceTime.get(i);
		 n.x=ir.XCoord.get(i);
		 n.y=ir.YCoord.get(i);
		 RS[k]=n;
		StationMap.put(n.id,k);
		 StationIDs.add(n.id);
		 k++;
		}
	}
	}
	int ind=0;
	for(int i=0; i<ir.NodeId.size();i++) {
		node n=new node();
		 n.id=ir.NodeId.get(i);
		 n.type=ir.Type.get(i);
		 n.demand=ir.Demand.get(i);
		 n.s=ir.ReadyTime.get(i);
		 n.e=ir.DueDate.get(i);
		 n.service_time=ir.ServiceTime.get(i);
		 n.x=ir.XCoord.get(i);
		 n.y=ir.YCoord.get(i);
	 if(ir.NodeId.get(i).contains("D")) {
		 Dep[ind]=n;
		 ListOfDeps.add(ir.NodeId.get(i));
		 N0[j0]=n;
		 j0++;
		 NodesMap.put(n.id, n);
		  for(ind=1;ind<num_veic;ind++ ) {
			 node n1=new node();
			 n1.id="D"+ind;
			 n1.type=n.type;
			 n1.demand=n.demand;
			 n1.s=n.s;
			 n1.e=n.e;
			 n1.service_time=n.service_time;
			 n1.x=n.x;
			 n1.y=n.y;
			 Dep[ind]=n1;
			 N0[j0]=n1;
			  NodeSImap.put(n.id,j0);
			 j0++;
			 NodesMap.put(n1.id, n1);
    		  ListOfDeps.add( n1.id);
			 }
	 }
	}
	M=Dep[0].e*10;
}

public void generate_distances() {
	N1=new node[Dep.length+N.length+RS.length];
	for(int i=0;i<Dep.length;i++) {
		N1[i]=Dep[i];
	}
	int j=Dep.length;
	for(int i=0;i<N.length;i++) {
		N1[j]=N[i];
		j++;
	}
	for(int i=0;i<RS.length;i++) {
		N1[j]=RS[i];
		j++;
	}
	
	D=new HashMap<String, HashMap<String, Double>>();
	for(int i=0;i<N1.length;i++) {
		for(j=0;j<N1.length;j++) {
			if(i!=j) {
				double b=Math.pow((N1[i].x-N1[j].x), 2);
				double c=Math.pow((N1[i].y-N1[j].y), 2);
				double a=b+c;
				HashMap<String, Double> D_supp=new HashMap<String, Double>();
								D_supp.put(N1[j].id, Math.sqrt(a));
				if(D.containsKey(N1[i].id))
					D.get(N1[i].id).put(N1[j].id,Math.sqrt(a));
				else
				{HashMap<String, Double> a_supp=new HashMap<String, Double>();
				a_supp.put(N1[j].id, Math.sqrt(a));
				D.put(N1[i].id, a_supp);
				}
			}
			else {
				HashMap<String, Double> D_supp=new HashMap<String, Double>();
				D_supp.put(N1[j].id, 0.0);
				if(D.containsKey(N1[i].id))
					D.get(N1[i].id).put(N1[j].id,0.0);
				else
				{HashMap<String, Double> a_supp=new HashMap<String, Double>();
				a_supp.put(N1[j].id, 0.0);
				D.put(N1[i].id, a_supp);
				}
				
			}
		}
	}
}

public void init_parameters(InstanceReaderGeneratorTech ir) {
	phi=ir.Inst.phi;
	sigma=ir.Inst.sigma;
	gamma=ir.Inst.gamma;
	Cl=ir.Inst.CL;
	Cb=ir.Inst.CB;
	FC=ir.Inst.F;
	FE=ir.Inst.fe;
	FD=ir.Inst.fd;
	v_min=new HashMap<IJind, Double>();//al momento abbiamo un solo valore
	v_max=new HashMap<IJind, Double>();//al momento abbiamo un solo valore
	for(int i=0;i<N1.length;i++)
		for(int j=0;j<N1.length;j++)
			if(i!=j) {
				if (!v_min.containsKey(new IJind(N1[i].id, N1[j].id))) {
					v_min.put(new IJind(N1[i].id, N1[j].id), ir.Inst.vmin);
					v_max.put(new IJind(N1[i].id, N1[j].id), ir.Inst.vmax);
				}
			}

	Q=new int[1]; //al momento abbiamo usato un solo valore
	P=new int[ir.Inst.NumOfLinesTimeSpeedApp];
	kq=new double[Q.length];
	bq=new double[Q.length];
	kp=new double[P.length];
	bp=new double[P.length];
	kq[0]=ir.Inst.K;
	bq[0]=ir.Inst.B;
	for(int i=0;i<P.length;i++) {
		kp[i]=ir.Inst.TimeSpeedAppKP[i];
		bp[i]=ir.Inst.TimeSpeedAppBP[i];
	}
}

public void add_velLimits(InstanceReaderGeneratorTech ir) {
		v_min=new HashMap<IJind, Double>();//al momento abbiamo un solo valore
		v_max=new HashMap<IJind, Double>();//al momento abbiamo un solo valore
		for(int i=0;i<N1.length;i++)
			for(int j=0;j<N1.length;j++)
				if(i!=j) {
					if (!v_min.containsKey(new IJind(N1[i].id, N1[j].id))) {
						v_min.put(new IJind(N1[i].id, N1[j].id), ir.Inst.vmin);
						v_max.put(new IJind(N1[i].id, N1[j].id), ir.Inst.vmax);
					}
				}
	}

public void writeXval(String str) throws FileNotFoundException, IloException {
	if(model.getStatus().equals(IloCplex.Status.Infeasible))return;
	pr = new PrintStream(new File("X_value_" + str + ".txt"));
	double minTol = 0.0000001;
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (model.getValue(varX.getValue()) >= 0.9) {
			pr.println("x(" + varX.getKey().xi + "," + varX.getKey().xj + "," + varX.getKey().staz + ") = " + model.getValue(varX.getValue()));
		}
	}

	for (Map.Entry<XIndex, IloNumVar> varB : b.entrySet()) {
		if (!varB.getKey().staz.equals("fictius"))
			if (model.getValue(varB.getValue()) >= minTol) {
				pr.println("b(" + varB.getKey().xi + "," + varB.getKey().xj + "," + varB.getKey().staz + ") = " + model.getValue(varB.getValue()));
			}
	}

	for (XIndex ijs : x.keySet()) {
		if (ijs.staz.equals("fictius")) {
			if (model.getValue(e.get(ijs)) >= minTol)
				pr.println("e_" + ijs.xi + "_" + ijs.xj + " = " + model.getValue(e.get(ijs)));
		} else {
			if (model.getValue(ea.get(ijs)) >= minTol)
				pr.println("ea_" + ijs.xi + "_" + ijs.xj + "_" + ijs.staz + " = " + model.getValue(ea.get(ijs)));
			if (model.getValue(eb.get(ijs)) >= minTol)
				pr.println("eb_" + ijs.xi + "_" + ijs.xj + "_" + ijs.staz + " = " + model.getValue(eb.get(ijs)));
		}
	}

	for (XIndex ijs : x.keySet()) {
		if (ijs.staz.equals("fictius")) {
			if (model.getValue(t.get(ijs)) >= minTol)
				pr.println("t_" + ijs.xi + "_" + ijs.xj + " = " + model.getValue(t.get(ijs)));
		} else {
			if (model.getValue(ta.get(ijs)) >= minTol)
				pr.println("ta_" + ijs.xi + "_" + ijs.xj + "_" + ijs.staz + " = " + model.getValue(ta.get(ijs)));
			if (model.getValue(tb.get(ijs)) >= minTol)
				pr.println("tb_" + ijs.xi + "_" + ijs.xj + "_" + ijs.staz + " = " + model.getValue(tb.get(ijs)));
		}
	}

	for (XIndex ijs : x.keySet()) {
		if (ijs.staz.equals("fictius")) {
			if (model.getValue(v.get(ijs)) >= minTol)
				pr.println("v_" + ijs.xi + "_" + ijs.xj + " = " + model.getValue(v.get(ijs)));
		} else {
			if (model.getValue(va.get(ijs)) >= minTol)
				pr.println("va_" + ijs.xi + "_" + ijs.xj + "_" + ijs.staz + " = " + model.getValue(va.get(ijs)));
			if (model.getValue(vb.get(ijs)) >= minTol)
				pr.println("vb_" + ijs.xi + "_" + ijs.xj + "_" + ijs.staz + " = " + model.getValue(vb.get(ijs)));
		}
	}

	for(int i=0;i<N.length;i++) {
		if (model.getValue(a.get(N[i].id)) >= minTol)
			pr.println("a_" + N[i].id + " = " + model.getValue(a.get(N[i].id)));
	}

	for(int i=1;i<N.length;i++) {
		if (model.getValue(r.get(N[i].id)) >= minTol)
			pr.println("r_" + N[i].id + " = " + model.getValue(r.get(N[i].id)));
	}

	for(int i=1;i<N.length;i++) {
		if (model.getValue(w.get(N[i].id)) >= minTol)
			pr.println("w_" + N[i].id + " = " + model.getValue(w.get(N[i].id)));
	}

	pr.close();
}

public void write_output(String namefil,String numRun, boolean init) throws FileNotFoundException, IloException {
	String file_name=namefil.replace(".txt", "");
	pr=new PrintStream(new File(file_name+"_output.txt"));
	//pr_excel=new PrintStream(new File(file_name+"_excel.csv"));
	pr_excel = new PrintStream(new File(file_name +"_"+numRun + "_excel.csv"));

	pr.println("number of 0-1 variables:"+conta_variabili_int);
	pr.println("number of continous variables:"+conta_variabili_cont);
	pr.println("number of constraints:"+conta_vincoli);																
	pr.println("objective function without service times at customers:"+model.getObjValue());
	double obj=model.getObjValue();
	double objcompleto = obj;
	for(int i=0;i<N.length;i++)
		objcompleto+=FD*N[i].service_time;

	//if(!init)
		pr_excel.println("Obj;"+obj+";Completo;"+objcompleto+"LB;"+model.getBestObjValue()+";gap;"+ model.getMIPRelativeGap()+";CostoVeicoli;"+
			model.getValue(CostoVeicoli)+";CostoEnergia;"+model.getValue(CostoEnergia)+";CostoDrivers;"+model.getValue(CostoDrivers));
	//else
	//	pr_excel.println("Obj;"+obj+";Completo;"+objcompleto+";gap;"+ model.getMIPRelativeGap()+";CostoVeicoli;"+
	//			model.getValue(CostoVeicoliInitSol)+";CostoEnergia;"+model.getValue(CostoEnergiaInitSol)+";CostoDrivers;"+model.getValue(CostoDriversInitSol));

	pr_excel.println("vehicle;node;node;ebattery;time;speed;demand;dist;service time;ready;due;tau;beta;resbatt;arr time;w;r_0");

	pr.println("objective function with service times at customers:"+obj);
	pr.println("time to solve:"+total_cplex_time);
	pr.println("solution status:"+model.getStatus());
	String currNode = "D0";
	HashMap<String, HashMap<String, Boolean>> xmap = new HashMap<String, HashMap<String, Boolean>>();
	int xi = 0;
	boolean FoundSucc = false;
	int Node2Visit = N.length;
	int numeroVeicolo = 0;
	int NumeroSosteRS = 0;
	boolean checkDep = false;

	while (!N0[xi].id.contains(currNode))
		xi++;

	if(!init) {
		if (ListOfDeps.contains(N0[xi].id))
			ListOfDeps.remove(N0[xi].id);
	}

	for(node n0:N0) {
		for(node n01:N0) {
			if(n0.id.equals(n01.id))continue;
			if (S_ij.containsKey(n0.id) && S_ij.get(n0.id).containsKey(n01.id)) {
				for (int s = 0; s < S_ij.get(n0.id).get(n01.id).size(); s++) {
					if (model.getValue(x.get(new XIndex(n0.id,n01.id,S_ij.get(n0.id).get(n01.id).get(s)))) >= 0.999)
					pr.println(numeroVeicolo+"-------"+n0.id+"----"+n01.id+"----"+S_ij.get(n0.id).get(n01.id).get(s));
				}
			}
		}
	}
	boolean firstCustFound = false;
	while(Node2Visit>=0) {
		if(!FoundSucc && checkDep) {
			if(!init)
				currNode = ListOfDeps.get(0);
			else
				currNode = "D0";

			xi=0;
			while (!N0[xi].id.contains(currNode))
				xi++;

			if(!init)
				ListOfDeps.remove(0);
			numeroVeicolo++;
		}

		FoundSucc = false;
		for (int j = 0; j < N0.length; j++) {
			if (xi != j && S_ij.containsKey(N0[xi].id) && S_ij.get(N0[xi].id).containsKey(N0[j].id)) {
				for (int s = 0; s < S_ij.get(N0[xi].id).get(N0[j].id).size(); s++) {
					
					if (xmap.containsKey(N0[xi].id) && xmap.get(N0[xi].id).containsKey(N0[j].id) )
						continue; // gi� usato

					if (model.getValue(x.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) >= 0.999) {
						pr.println(numeroVeicolo+"-------"+N0[xi].id+"----"+N0[j].id);
						checkDep = false;
						// il succ � customer
						if (S_ij.get(N0[xi].id).get(N0[j].id).get(s).equals("fictius")) {
							pr_excel.print(numeroVeicolo + ";" + N0[xi].id + ";" + N0[j].id + ";" +
									model.getValue(e.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									model.getValue(t.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									model.getValue(v.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									N0[j].demand + ";" + D.get(N0[xi].id).get(N0[j].id) + ";" + N0[j].service_time + ";" + N0[j].s + ";" + N0[j].e + ";0;0;"
							);
							if (!N0[j].id.contains("D")) {
								pr_excel.print(model.getValue(r.get(N0[j].id)) + ";" + model.getValue(a.get(N0[j].id)) + ";" + model.getValue(w.get(N0[j].id))+";");
								if(N0[xi].id.contains("D"))
									pr_excel.println(model.getValue(r_v_0.get(N0[xi].id)));
								else
									pr_excel.println();
							}
							else
								pr_excel.println("0;" + model.getValue(a.get(N0[j].id)) + ";0");

							currNode = N0[j].id;

							if(xmap.containsKey(N0[xi].id) && !xmap.get(N0[xi].id).containsKey(N0[j].id))
							{
								xmap.get(N0[xi].id).put(N0[j].id,true);
							}
							else
							{
								HashMap<String, Boolean> Nodoj = new HashMap<String, Boolean>();
								Nodoj.put(N0[j].id, true);
								xmap.put(N0[xi].id, Nodoj);
							}

							FoundSucc = true;
							xi=j;
							Node2Visit--;
							if (Node2Visit>=0 && currNode.contains("D") && (ListOfDeps.size()>0 || init)) {
								Node2Visit++;
								numeroVeicolo++;

								if(!init)
									currNode = ListOfDeps.get(0);

								xi=0;

								while (!N0[xi].id.contains(currNode))
									xi++;

								if(!init)
									ListOfDeps.remove(0);

								checkDep=true;
							}
							break;
						} else if (!S_ij.get(N0[xi].id).get(N0[j].id).get(s).equals("fictius")) {
							NumeroSosteRS++;

							double valBeta = 0.0;
							double valTau = 0.0;
							String NodoXi = N0[xi].id;
							String NodoXj = N0[j].id;
							String Stz = S_ij.get(N0[xi].id).get(N0[j].id).get(s);
							int contaBeta=0;
							for (IJSRIndex ijsr:IJSRindici.stream().filter(e-> e.xi.equals(NodoXi) && e.xj.equals(NodoXj) && e.staz.equals(Stz)).collect(Collectors.toList()))
							{
								if(model.getValue(beta.get(ijsr)) > 0.00001){
									if(contaBeta>0)
										System.err.println("******** More than one Beta for "+ijsr.xi+"_"+ijsr.xj+"_"+ijsr.staz);
									valBeta = model.getValue(beta.get(ijsr));
									contaBeta++;
								}
							}
							valTau = model.getValue(b.get(new XIndex(NodoXi,NodoXj,Stz)));
							pr_excel.print(numeroVeicolo + ";" + N0[xi].id + ";" + S_ij.get(N0[xi].id).get(N0[j].id).get(s) + ";" +
									model.getValue(ea.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									model.getValue(ta.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s))))+ ";" +
									model.getValue(va.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									"0" + ";" + D.get(N0[xi].id).get(S_ij.get(N0[xi].id).get(N0[j].id).get(s)) + ";" +
									"0" + ";" + RS[StationMap.get(S_ij.get(N0[xi].id).get(N0[j].id).get(s))].s + ";" + RS[StationMap.get(S_ij.get(N0[xi].id).get(N0[j].id).get(s))].e +
									";"+valTau+";"+valBeta+";");
							if (!N0[j].id.contains("D")) {
								if (!N0[xi].id.contains("D"))
									pr_excel.println(gamma + ";" + (model.getValue(a.get(N0[xi].id)) + N0[xi].service_time + model.getValue(w.get(N0[xi].id)) +
											model.getValue(ta.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s))))) +
											";" + "0");//model.getValue(w.get(N0[j].id)));
								else
									pr_excel.println(gamma + ";" + (model.getValue(ta.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s))))) +
											";" + "0"+";"+model.getValue(r_v_0.get(N0[xi].id)));//model.getValue(w.get(N0[j].id)));
							}
							else
								pr_excel.println("0;"+(model.getValue(a.get(N0[xi].id)) + N0[xi].service_time + model.getValue(w.get(N0[xi].id)) +
										model.getValue(ta.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))))+";0");

							pr_excel.print(numeroVeicolo + ";" + S_ij.get(N0[xi].id).get(N0[j].id).get(s) + ";" +
									N0[j].id + ";" +
									model.getValue(eb.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									model.getValue(tb.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									model.getValue(vb.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									N0[j].demand + ";" +
									D.get(S_ij.get(N0[xi].id).get(N0[j].id).get(s)).get(N0[j].id) +";"+ N0[j].service_time + ";" + N0[j].s + ";" + N0[j].e + ";0;0;");
							if (!N0[j].id.contains("D"))
								pr_excel.println(model.getValue(r.get(N0[j].id)) + ";" + model.getValue(a.get(N0[j].id)) + ";" + model.getValue(w.get(N0[j].id)));
							else
								pr_excel.println("0;"+model.getValue(a.get(N0[j].id))+";0");

							currNode = N0[j].id;

							if(xmap.containsKey(N0[xi].id) && !xmap.get(N0[xi].id).containsKey(N0[j].id))
							{
								xmap.get(N0[xi].id).put(N0[j].id,true);
							}
							else
							{
								HashMap<String, Boolean> Nodoj = new HashMap<String, Boolean>();
								Nodoj.put(N0[j].id, true);
								xmap.put(N0[xi].id, Nodoj);
							}
							FoundSucc = true;
							xi=j;
							Node2Visit--;

							if (Node2Visit>=0 && currNode.contains("D") && (ListOfDeps.size()>0 || init)) {
								Node2Visit++;
								numeroVeicolo++;

								if(!init)
									currNode = ListOfDeps.get(0);

								xi=0;
								while (!N0[xi].id.contains(currNode))
									xi++;

								if(!init)
									ListOfDeps.remove(0);

								checkDep=true;
							}
							break;
						}
					}
					
				}
			}

			if (FoundSucc) {
				firstCustFound = true;
				break;
			}
			else if(!firstCustFound) {
				checkDep=true;
			}

		}
	}

	pr_excel.close();
	for(int i=0;i<N0.length;i++)
		for(int j=0;j<N0.length;j++)
			if(i!=j&&S_ij.containsKey(N0[i].id)&&S_ij.get(N0[i].id).containsKey(N0[j].id)) {
				for(int s=0;s<S_ij.get(N0[i].id).get(N0[j].id).size();s++) {
					if(model.getValue(x.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s))))>=0.999) {
						pr.println("from "+N0[i].id+" to "+N0[j].id+" with stop at station "+S_ij.get(N0[i].id).get(N0[j].id).get(s)+" x value:"+model.getValue(x.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));
						pr.println(x.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s))).getName());
					if(!S_ij.get(N0[i].id).get(N0[j].id).get(s).equals("fictius")) {
						pr.println(" battery from "+N0[i].id+" to "+S_ij.get(N0[i].id).get(N0[j].id).get(s)+":"+model.getValue(ea.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));
						pr.println(" time from "+N0[i].id+" to "+S_ij.get(N0[i].id).get(N0[j].id).get(s)+":"+model.getValue(ta.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));
						pr.println(" speed from "+N0[i].id+" to "+S_ij.get(N0[i].id).get(N0[j].id).get(s)+":"+model.getValue(va.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));

						pr.println(" battery from "+S_ij.get(N0[i].id).get(N0[j].id).get(s)+" to " +N0[j].id+":"+model.getValue(eb.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));
						pr.println(" time from "+S_ij.get(N0[i].id).get(N0[j].id).get(s)+" to " +N0[j].id+":"+model.getValue(tb.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));
						pr.println(" speed from "+S_ij.get(N0[i].id).get(N0[j].id).get(s)+" to " +N0[j].id+":"+model.getValue(vb.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));
					}
					else {
						pr.println(" battery from "+N0[i].id+" to "+N0[j].id+":"+model.getValue(e.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));
						pr.println(" time from "+N0[i].id+" to "+N0[j].id+":"+model.getValue(t.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));
						pr.println(" speed from "+N0[i].id+" to "+N0[j].id+":"+model.getValue(v.get(new XIndex(N0[i].id,N0[j].id,S_ij.get(N0[i].id).get(N0[j].id).get(s)))));
					}
					}
				}
			}
	int count_veic=0;
	for(node no:Dep) {
	for(int j=0;j<N.length;j++) {
		if(S_ij.containsKey(no.id)&&S_ij.get(no.id).containsKey(N[j].id)) { // j!=0&&
			for(int s=0;s<S_ij.get(no.id).get(N[j].id).size();s++)
				if(model.getValue(x.get(new XIndex(no.id,N[j].id,S_ij.get(no.id).get(N[j].id).get(s))))>=0.999)
					count_veic++;
		}
	}
	}

	pr.println("number of vehicles used:"+count_veic);

	try(FileWriter fw = new FileWriter("RiassResults.csv", true);
		BufferedWriter bw = new BufferedWriter(fw);
		PrintWriter outpr = new PrintWriter(bw)) {
		//if(!init)
			outpr.println(file_name + ";" + model.getStatus() + ";" + model.getObjValue() + ";" + count_veic + ";" + model.getValue(CostoVeicoli) + ";" + model.getValue(CostoEnergia) + ";" +
				model.getValue(CostoDrivers) + ";" + total_cplex_time + ";" + model.getMIPRelativeGap()+";CLNLS");
		//else
		//	outpr.println(file_name + ";" + model.getStatus() + ";" + model.getObjValue() + ";" + count_veic + ";" + model.getValue(CostoVeicoliInitSol) + ";" + model.getValue(CostoEnergiaInitSol) + ";" +
		//			model.getValue(CostoDriversInitSol) + ";" + total_cplex_time + ";" + model.getMIPRelativeGap()+";CLNLS");
		outpr.close();
	} catch (IOException e) {
		System.err.println("Errore scrittura file RiassResult.csv: "+e);
	}

pr.close();

	PrintStream soste = new PrintStream(new FileOutputStream("Soste.csv", true));
	soste.println(file_name+";"+model.getObjValue()+";"+NumeroSosteRS);
	soste.close();

}
public void add_deps() {
	int ind=Dep.length;
	int ind1=N0.length;
	node[] Dep_1=new node[num_veic];
	node[] N0_1=new node[N0.length+num_veic-1];
		
	Dep_1[0]=Dep[0];
	for(int i=0;i<N0.length;i++)
		N0_1[i]=N0[i];
	
	 for(int i=1;i<num_veic;i++) {
		 node n=new node();
		 n.id="D"+i;
		 n.type=Dep[0].type;
		 n.demand=Dep[0].demand;
		 n.s=Dep[0].s;
		 n.e=Dep[0].e;
		 n.service_time=Dep[0].service_time;
		 n.x=Dep[0].x;
		 n.y=Dep[0].y;
		 Dep_1[i]=n;
		 ListOfDeps.add(n.id);
		 N0_1[ind1++]=n;
		 NodesMap.put(n.id, n);
	 }
		Dep=new node[Dep_1.length];
		N0=new node[N0_1.length];
		for(int i=0;i<N0_1.length;i++)
			N0[i]=N0_1[i];
		for(int i=0;i<Dep_1.length;i++)
			Dep[i]=Dep_1[i];
		  
	 
}

public void add_new_deps(int numaddDep) {
		int ind=Dep.length;
		int ind1=N0.length;
		node[] Dep_1=new node[num_veic];
		node[] N0_1=new node[N0.length+numaddDep];

		for(int i = 0;i< Dep.length;i++)
			Dep_1[i]=Dep[i];

		for(int i=0;i<N0.length;i++)
			N0_1[i]=N0[i];

		for(int i=ind;i<num_veic;i++) {
			node n=new node();
			n.id="D"+i;
			n.type=Dep[0].type;
			n.demand=Dep[0].demand;
			n.s=Dep[0].s;
			n.e=Dep[0].e;
			n.service_time=Dep[0].service_time;
			n.x=Dep[0].x;
			n.y=Dep[0].y;
			Dep_1[i]=n;
			ListOfDeps.add(n.id);
			N0_1[ind1++]=n;
			NodesMap.put(n.id, n);
		}
		Dep=new node[Dep_1.length];
		N0=new node[N0_1.length];
		for(int i=0;i<N0_1.length;i++)
			N0[i]=N0_1[i];
		for(int i=0;i<Dep_1.length;i++)
			Dep[i]=Dep_1[i];
	}

public int storeSol() throws  IloException {
int numVeicUsatiSolInit = 0;
x_init.clear();
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (model.getValue(varX.getValue()) >= 0.99) {
			x_init.add(varX.getKey());
			if(varX.getKey().xi.startsWith("D"))
				numVeicUsatiSolInit++;
		}
	}
	return  numVeicUsatiSolInit;
}

public void setWarmStart_OLD(int usedVeic) throws IloException {
// sostituisco le partenze e arrivi multipli da D0
int currDepStart = 0;
//int currDepArr = 0;
String currNextNode = "";
String currDep = "D"+currDepStart;
ArrayList<XIndex> copy_x_init = new ArrayList<>();

	for (XIndex startDep: x_init.stream().filter(e-> e.xi.equals("D0")).collect(Collectors.toList())) {
		// trova le partenze da D0
		XIndex successor = null;
		currDep = "D"+currDepStart;
		copy_x_init.add(new XIndex(currDep, startDep.xj, startDep.staz));
		currNextNode = startDep.xj;
		while(!currNextNode.equals("D0")) {
			String theNext = currNextNode;
			successor = x_init.stream().filter(e -> e.xi.equals(theNext)).findAny().orElse(null);
			if(successor==null)
			{
				System.out.println("Errore decodifica sol per warm start");
				System.exit(0);
			}
			currNextNode = successor.xj;
		}
		copy_x_init.add(new XIndex(successor.xi, currDep, successor.staz));
		currDepStart++;
	}

	for(int i=0; i<x_init.size(); i++) {
		XIndex el = x_init.get(i);
		/*if (el.xi.equals("D0")) {
			copy_x_init.add(new XIndex("D" + currDepStart, el.xj, el.staz));
			currDepStart++;
		} else if (el.xj.equals("D0")) {
			copy_x_init.add(new XIndex(el.xi, "D" + currDepArr, el.staz));
			currDepArr++;
		}*/
		if (el.xi.equals("D0") || el.xj.equals("D0"))
			continue;
		else
			copy_x_init.add(el);
	}

int numVarWS = Xindici.size() ;

	IloNumVar[] flat_varX = new IloNumVar[numVarWS];
	double[] flat_varX_startingVals = new double[numVarWS];

	int j=0;
	for(XIndex xind:Xindici)
	{
		flat_varX[j] = x.get(xind);
		if(copy_x_init.contains(xind)){
			flat_varX_startingVals[j] = 1.0;
		}
		else {
			flat_varX_startingVals[j] = 0.0;
		}
		j++;
	}
	/*
	for(int i=0; i<copy_x_init.size(); i++){
			flat_varX[i] = x.get(copy_x_init.get(i));
			flat_varX_startingVals[i] = 1.0;
		//x.get(copy_x_init.get(i)).setLB(1.0);
	}

	int j = copy_x_init.size();
	for(XIndex xind:Xindici)
		if(!copy_x_init.contains(xind)){
			flat_varX[j] = x.get(xind);
			flat_varX_startingVals[j] = 0.0;
			//x.get(xind).setUB(0.0);
			j++;
		}
  */
	while(model.getNMIPStarts()>0)
		model.deleteMIPStarts(0);

	int mps = model.addMIPStart(flat_varX, flat_varX_startingVals, IloCplex.MIPStartEffort.Auto, "addMIPStart start");

}

public void setWarmStart(int usedVeic) throws IloException {

// sostituisco le partenze e arrivi multipli dai depot
		String currNextNode = "";
		String currDep = "";
		ArrayList<XIndex> copy_x_init = new ArrayList<>();

		for (XIndex startDep: x_init.stream().filter(e-> e.xi.startsWith("D")).collect(Collectors.toList())) {
			// trova le partenze dai dep
			XIndex successor = null;
			currDep = startDep.xi;
			copy_x_init.add(new XIndex(currDep, startDep.xj, startDep.staz));
			currNextNode = startDep.xj;
			while(!currNextNode.startsWith("D")) {
				String theNext = currNextNode;
				successor = x_init.stream().filter(e -> e.xi.equals(theNext)).findAny().orElse(null);
				if(successor==null)
				{
					System.out.println("Errore decodifica sol per warm start");
					System.exit(0);
				}
				currNextNode = successor.xj;
			}
			copy_x_init.add(new XIndex(successor.xi, currDep, successor.staz));
		}

		for(int i=0; i<x_init.size(); i++) {
			XIndex el = x_init.get(i);
			if (el.xi.startsWith("D") || el.xj.startsWith("D"))
				continue;
			else
				copy_x_init.add(el);
		}

		int numVarWS = Xindici.size() ;
		IloNumVar[] flat_varX = new IloNumVar[numVarWS];
		double[] flat_varX_startingVals = new double[numVarWS];

		int j=0;
		for(XIndex xind:Xindici)
		{
			flat_varX[j] = x.get(xind);
			if(copy_x_init.contains(xind)){
				flat_varX_startingVals[j] = 1.0;
			}
			else {
				flat_varX_startingVals[j] = 0.0;
			}
			j++;
		}

		while(model.getNMIPStarts()>0)
			model.deleteMIPStarts(0);

		int mps = model.addMIPStart(flat_varX, flat_varX_startingVals, IloCplex.MIPStartEffort.Auto, "addMIPStart start");

	}

public static void main(String[] args) throws  IOException, IloException {
	System.out.println("PROVA");
	InstanceReaderGeneratorTech ir = new InstanceReaderGeneratorTech();
	ir.generate(args);
	System.out.println("Cloneless tech 2");
	cloneless_tech2 jc=new cloneless_tech2();

	jc.TempoCPU = System.currentTimeMillis();

	jc.num_veic=Integer.parseInt(args[5]);
	if(args[2].contains("01"))
		jc.epsilon=0.1;
	else
		jc.epsilon=1.0;
	timeLimit = Double.parseDouble(args[6]);
	//jc.rho = Double.parseDouble(args[7]);

	jc.R = new	RechargeTypes[ir.Inst.NumTechs];
	for (int tec = 0; tec<ir.Inst.NumTechs;tec++){
		jc.R[tec] = new RechargeTypes();
		jc.R[tec].id=Integer.parseInt(ir.Inst.TechId[tec]);
		jc.R[tec].description="Tec_"+tec;
		jc.R[tec].cost = ir.Inst.EnCost[tec];
		jc.R[tec].speed = ir.Inst.RecSpeed[tec];
		if(ir.Inst.RecSpeed[tec] < jc.speed_min)
			jc.speed_min = ir.Inst.RecSpeed[tec];
	}

	for(Map.Entry<String,ArrayList<String>> StaTec: ir.Inst.RSTech.entrySet())
	{
		String stat = StaTec.getKey();
	 	ArrayList<RechargeTypes> Rlist = new ArrayList<>();
		 for (int tec=0;tec<StaTec.getValue().size();tec++) {
			 String tecId = StaTec.getValue().get(tec);
			 RechargeTypes Relem = new RechargeTypes();
			 Relem.id=Integer.parseInt(StaTec.getValue().get(tec));
			 for (int tt=0;tt<ir.Inst.TechId.length;tt++) {
				 if (ir.Inst.TechId[tt].equals(tecId)) {
					 Relem.speed = ir.Inst.RecSpeed[tt];
					 Relem.cost = ir.Inst.EnCost[tt];
					 Relem.description = "Tec_" + tt;
					 Rlist.add(Relem);
				 }
			 }
		 }
		 jc.RRT.put(stat,Rlist);
	}

	if(true)
	{ // cloneless tech 2 normale
		jc.generate_nodes(ir);
		jc.generate_distances();
		jc.init_parameters(ir);
		jc.compute_S();
		jc.model= new IloCplex();
		jc.Cplex_time_spent=jc.model.getCplexTime();
		jc.initVariables_Ok();
		jc.initModel(jc);
		//jc.model.exportModel("model_tech_4.lp");
		jc.solve(args[0]);
		jc.write_output(args[1], (args.length>=16? args[15] : ""), false);
	}
	else
	{ // cloneless tech 2 con inizializzazione
		trueNumVeic = jc.num_veic;
		int VeicoliSolInit = 0;

		// inizializzazione
		//jc.num_veic = 1;
		{
			jc.generate_nodes(ir);
			jc.generate_distances();
			jc.init_parameters(ir);
			jc.compute_S();
			//jc.initVariablesOneVeic();
			jc.initVariables_Ok();
			System.out.println("prima d init " + (System.currentTimeMillis() - jc.TempoCPU) / 1000.0);
			jc.initModelOneVeic(jc,trueNumVeic);

			// annullo variabili con soste a stazioni e che partono o arrivano da dep diversi da D0
			for(XIndex xind: jc.Xindici){
				if(!xind.staz.equals("fictius")) {
					jc.x.get(xind).setUB(0.0);
					jc.ZeroedXvar.add(xind); // tutte le var con stazioni sono inizialmente fissate a zero
				}
				else if((xind.xi.startsWith(("D")) && !xind.xi.equals("D0")) || (xind.xj.startsWith(("D")) && !xind.xj.equals("D0")))
				{
					jc.x.get(xind).setUB(0.0);
					jc.ZeroedXvar.add(xind);
				}
			}
			/*
			// ammetto SOC negativi
			for(int ind=0; ind<jc.N.length;ind++)
				jc.r.get(jc.N[ind].id).setLB(Double.NEGATIVE_INFINITY);
*/
			//jc.model.exportModel("model_tech_red_prima.lp");
			jc.solve(args[0]);
			jc.write_output(args[1], (args.length>=16? args[15] : ""),true);
			VeicoliSolInit = jc.storeSol();
		}

		if (VeicoliSolInit > trueNumVeic) // solo se fosse > oppure anche se fosse < ?
			trueNumVeic = VeicoliSolInit;
		jc.num_veic = trueNumVeic;

		// ripristino variabili con soste a stazioni e partenze dai depositi copie di D0
		for(XIndex xind: jc.ZeroedXvar){
				jc.x.get(xind).setUB(1.0);
		}

		{
		//	jc.add_deps();
		//	jc.generate_distances();
		//	jc.init_parameters(ir);
		//	jc.compute_S();
		//	jc.initiVariables();
		//	jc.AddVariables();
		//	jc.add_constraints();
		//	jc.add_new_constraints();
			jc.add_constraint_4_();
			jc.add_constraint_17_();
			jc.add_constraint_18_();
			System.out.println("18 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			jc.add_constraint_19_();
			System.out.println("19 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			jc.add_constraint_20_();
			System.out.println("20 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			jc.add_constraint_21_();
			System.out.println("21 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			jc.add_constraint_22_();
			System.out.println("22 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			jc.add_constraint_23a_();
			System.out.println("23a - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			jc.add_constraint_23b_();
			System.out.println("23b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			//commentiamo i vincoli 24 perch� doppiano i 26
			//add_constraint_24_();
			//System.out.println("24 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			jc.add_constraint_25ab_();
			System.out.println("25ab - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			//add_constraint_25b_();
			//System.out.println("25b - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			jc.add_constraint_26_();
			System.out.println("26 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);
			jc.add_constraint_27_();
			System.out.println("27 - " + (System.currentTimeMillis() - jc.TempoCPU)/1000.0);

			// warm start
			ArrayList<IloRange> warmStartConstr = new ArrayList<>();
			for(XIndex vx: jc.x_init)
			{
				//impongo che la sommatoria di x_ijs (incluso la stazione fittizia) sia 1
				IloLinearNumExpr exp= jc.model.linearNumExpr();
				if(vx.xi.equals(("D0")))
				{
					for(Map.Entry<XIndex,IloNumVar> varX: jc.x.entrySet())
						if(varX.getKey().xi.startsWith("D")  && varX.getKey().xj.equals(vx.xj) ) {
							exp.addTerm(1, varX.getValue());
						}
				}
				else if( vx.xj.equals("D0"))
				{
					for(Map.Entry<XIndex,IloNumVar> varX: jc.x.entrySet())
						if(varX.getKey().xj.startsWith("D")  && varX.getKey().xi.equals(vx.xi) ) {
							exp.addTerm(1, varX.getValue());
						}
				}
				else  {
					for(Map.Entry<XIndex,IloNumVar> varX: jc.x.entrySet())
						if(varX.getKey().xi.equals(vx.xi) && varX.getKey().xj.equals(vx.xj)){
							exp.addTerm(1, varX.getValue());
						}
				}

				warmStartConstr.add( jc.model.addEq(exp, 1, "Constr_warmstart_"+vx.xi+"_"+vx.xj));
			}
			//jc.model.exportModel("model_tech_red_ws.lp");
			jc.solve("");
			VeicoliSolInit = jc.storeSol();
			jc.write_output(args[1], (args.length>=16? args[15] : ""),false);
			for (IloRange constrWS: warmStartConstr) {
				jc.model.delete(constrWS);
			}
			jc.setWarmStart(VeicoliSolInit);

			//jc.model.exportModel("model_tech_red.lp");
			jc.solve(args[0]);
			jc.write_output(args[1], (args.length>=16? args[15] : ""),false);
		}
	}
return;
}

}
