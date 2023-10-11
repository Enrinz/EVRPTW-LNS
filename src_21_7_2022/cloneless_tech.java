import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Questa classe implementa il modello proposto nel paper JCP del 2019
 * "Development of energy consumption optimization model for the
electric vehicle routing problem with time windows"
 * @author Ornella Pisacane
 *
 */

public class cloneless_tech {
	public static PrintStream prMod=null;
	public static PrintStream verbCloneless = System.out;
	int num_veic;
	node[] N, N0;//set di clienti
	//HashMap<String, Integer>NodeSImap = new HashMap<String, Integer>();
	HashMap<String, Integer>StationMap = new HashMap<String, Integer>();
	HashMap<String, node>NodesMap = new HashMap<String, node>();
	ArrayList<String> StationIDs = new ArrayList<String>();
	node[] RS;// set delle stazioni
	node[] N1;//set di tutti i nodi
	HashMap<String, HashMap<String, Double>> D;//distanze tra coppie di nodi i,j
	HashMap<String, HashMap<String, Double>> surrogate_D;//distanze surrogate tra coppie di nodi i,j
	HashMap<IJind, Double>v_min,v_max;//v_min e v_max sono le velocità minime e massime fra ogni coppia di nodi
	HashMap<String, HashMap<String, ArrayList<String>>> S_ij=new HashMap<String, HashMap<String, ArrayList<String>>>();
	ArrayList<XIndex> Xindici = new ArrayList<XIndex>();
	ArrayList<IJind> IJindici = new ArrayList<IJind>();
	ArrayList<IJind> Findici = new ArrayList<IJind>();
	HashMap<XIndex, IloConversion>XConv;
	//la minima e la massima velocità consentita sull'arco i e j
	double epsilon, Cl,Cb,FC,FE,FD, phi,sigma, M, gamma, rho;//rispetto al paper F è stato sotituito con FC
	int[] P, Q;
	double[] kp, bp, kq, bq;
	int timeLimitMIPiter=0;
	
//nuovi parametri
	node[] Dep;//set dei depositi
	ArrayList<String> ListOfDeps = new ArrayList<>();
	RechargeTypes[] R;//diverse tecnologie di ricarica
	double speed_min=Double.POSITIVE_INFINITY; //velocità di ricarica minima
	HashMap<String,ArrayList<RechargeTypes>> RRT=new HashMap<String,ArrayList<RechargeTypes>>();//sotto-insieme di tecnologie di ricarica disponibili ad una stazione
	ArrayList<IJSRIndex> IJSRindici = new ArrayList<IJSRIndex>();
	
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


IloRange newVinc, newVinc2;
IloLinearNumExpr ob;
IloObjective obiett;

// funzione obj
	IloNumVar CostoVeicoli;
	IloNumVar CostoDrivers;
	IloNumVar CostoEnergia;

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
			if(N1[i].id.contains("D")&&N1[j].id.contains("D")) continue;
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
	
	
	for(String id:S_ij.keySet()) {
		for (String id_1 : S_ij.get(id).keySet()) {
			
			for (String id_2 : S_ij.get(id).get(id_1)) {
				Xindici.add(new XIndex(id, id_1, id_2));
				if (!IJindici.contains(new IJind(id, id_1))) {
					IJindici.add(new IJind(id, id_1));
					Findici.add(new IJind(id, id_1));
				}
				if (!id_2.equals("fictius") && !IJindici.contains(new IJind(id, id_2)))
					IJindici.add(new IJind(id, id_2));
				if (!id_2.equals("fictius") && !IJindici.contains(new IJind(id_2, id_1)))
					IJindici.add(new IJind(id_2, id_1));
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

public void initModel() throws IloException {
	add_objective_function();
	System.err.println("Funzione obiettivo aggiunta");
	add_constraint_2_();
	System.err.println("Vincoli 2 aggiunti");
	add_constraint_3_();
	System.err.println("Vincoli 3 aggiunti");
	add_constraint_4_();
	System.err.println("Vincoli 4 aggiunti");
	add_constraint_5_();
	System.err.println("Vincoli 5 aggiunti");
	add_constraint_6_();
	System.err.println("Vincoli 6 aggiunti");
	add_constraint_7_();
	System.err.println("Vincoli 7 aggiunti");
	add_constraint_8_();
	System.err.println("Vincoli 8 aggiunti");
	add_constraint_9_();
	System.err.println("Vincoli 9 aggiunti");
	add_constraint_10_();
	System.err.println("Vincoli 10 aggiunti");
	add_constraint_11_();
	System.err.println("Vincoli 11 aggiunti");
	add_constraint_12a_();
	System.err.println("Vincoli 12a aggiunti");
	add_constraint_12b_();
	System.err.println("Vincoli 12b aggiunti");
	add_constraint_13a_();
	System.err.println("Vincoli 13a aggiunti");
	add_constraint_13b_();
	System.err.println("Vincoli 13b aggiunti");
	add_constraint_14_();
	System.err.println("Vincoli 14 aggiunti");
	add_constraint_15_();
	System.err.println("Vincoli 15 aggiunti");
	add_constraint_16_();
	System.err.println("Vincoli 16 aggiunti");
	add_constraint_17_();
	System.err.println("Vincoli 17 aggiunti");
	add_constraint_18_();
	System.err.println("Vincoli 18 aggiunti");
	add_constraint_19_();
	System.err.println("Vincoli 19 aggiunti");
	add_constraint_20_();
	System.err.println("Vincoli 20 aggiunti");
	add_constraint_21_();
	System.err.println("Vincoli 21 aggiunti");
	add_constraint_22_();
	System.err.println("Vincoli 22 aggiunti");
	add_constraint_23a_();
	System.err.println("Vincoli 23a aggiunti");
	add_constraint_23b_();
	System.err.println("Vincoli 23b aggiunti");
	add_constraint_24_();
	System.err.println("Vincoli 24 aggiunti");
	add_constraint_25a_();
	System.err.println("Vincoli 25a aggiunti");
	add_constraint_25b_();
	System.err.println("Vincoli 25b aggiunti");
	add_constraint_26_();
	System.err.println("Vincoli 26 aggiunti");
	add_constraint_27_();
	System.err.println("Vincoli 27 aggiunti");
	add_constraint_28_();
	System.err.println("Vincoli 28 aggiunti");
	add_constraint_29_();
	System.err.println("Vincoli 29 aggiunti");
	add_constraint_30_();
	System.err.println("Vincoli 30 aggiunti");
	add_constraint_31_();
	System.err.println("Vincoli 31 aggiunti");
	add_constraint_32_();
	System.err.println("Vincoli 32 aggiunti");
	
}

public void initVariables() throws FileNotFoundException, IloException {

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
	r_v_0.put(Dep[ind].id,model.numVar(Dep[ind].s,Dep[ind].e,IloNumVarType.Float,"r_v^0"+Dep[ind].id));
	conta_variabili_cont++;
}
//new
for ( int ind=0; ind<IJSRindici.size();ind++) {
	beta.put(IJSRindici.get(ind), model.numVar(0,gamma-sigma,IloNumVarType.Float,"beta_"+IJSRindici.get(ind).xi+"_"+IJSRindici.get(ind).xj+"_"+IJSRindici.get(ind).staz+"_"+IJSRindici.get(ind).tec));
	conta_variabili_cont++;
	
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

public void solve(String file_model) throws IloException, FileNotFoundException {
	String name_model=file_model.replace(".txt", "");

	//model.setParam(IloCplex.Param.MIP.Tolerances.MIPGap,0.01);
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
		model.solve();
		total_cplex_time = model.getCplexTime() - cplexTime;
	//status = model.getStatus(); //****
	//System.out.println((nsol++) + ";" + status + ";" +total_cplex_time+";"+ model.getObjValue() + ";" + model.getMIPRelativeGap()); //***
	//} //***

}

public void add_constraint(ArrayList<XIndex> bucket, double Z_H) throws IloException {
		
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

// Penalizzazione r < sigma
//	for(int i=0;i<N.length;i++) {
//		ob.addTerm(1000000.0, dr.get(N[i].id));
//	}

IloLinearNumExpr obCV= model.linearNumExpr();
obCV.addTerm(1.0,CostoVeicoli);

for(XIndex xind:x.keySet()){
	if(xind.xi.contains("D")){
		obCV.addTerm(-FC, x.get(xind));
		ob.addTerm(FC, x.get(xind));
	}
}

model.addEq(obCV,0.0,"CostoVeic");

IloLinearNumExpr obCE= model.linearNumExpr();
obCE.addTerm(1.0,CostoEnergia);
/*for(XIndex ijs: Xindici){
	if(ijs.staz.equals("fictius")) {
		obCE.addTerm(-Cb * FE * D.get(ijs.xi).get(ijs.xj), e.get(ijs));
		ob.addTerm(Cb*FE*D.get(ijs.xi).get(ijs.xj), e.get(ijs));
	}
	else {
		obCE.addTerm(-Cb * FE * D.get(ijs.xi).get(ijs.staz), ea.get(ijs));
		ob.addTerm(Cb*FE*D.get(ijs.xi).get(ijs.staz), ea.get(ijs));
		obCE.addTerm(-Cb * FE * D.get(ijs.staz).get(ijs.xj), eb.get(ijs));
		ob.addTerm(Cb*FE*D.get(ijs.staz).get(ijs.xj), eb.get(ijs));
	}
}*/
for(node n:Dep)//solo una tecnologia disponibile al deposito
{	
	obCE.addTerm(-Cb*FE*RRT.get("S0").get(0).cost, r_v_0.get(n.id));
	ob.addTerm(Cb*FE*RRT.get("S0").get(0).cost, r_v_0.get(n.id));
}
for(IJSRIndex ijsr:IJSRindici) {
	for(RechargeTypes rtt:RRT.get(ijsr.staz)) {
		obCE.addTerm(-Cb*FE*rtt.cost, beta.get(ijsr));
		ob.addTerm(Cb*FE*rtt.cost, beta.get(ijsr));
	}
}
model.addEq(obCE,0.0,"CostoEn");

IloLinearNumExpr obCD= model.linearNumExpr();
obCD.addTerm(1.0,CostoDrivers);

	for(XIndex ijs: Xindici){
		if(ijs.staz.equals("fictius")) {
			obCD.addTerm(-FD, t.get(ijs));
			ob.addTerm(FD, t.get(ijs));
		}
		else {
			obCD.addTerm(-FD, ta.get(ijs));
			ob.addTerm(FD, ta.get(ijs));
			obCD.addTerm(-FD, tb.get(ijs));
			ob.addTerm(FD, tb.get(ijs));
			obCD.addTerm(-FD, b.get(ijs));
			ob.addTerm(FD, b.get(ijs));
		}
	}

for(int i=0;i<N.length;i++) {
	obCD.addTerm(-FD, w.get(N[i].id));
	ob.addTerm(FD, w.get(N[i].id));
	//prMod.println(FD+" W_"+N[i].id);
}
model.addEq(obCD,0.0,"CostoDriv");

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

	for(int j=0;j<N.length;j++) {
		IloLinearNumExpr exp= model.linearNumExpr();
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet())
			if(varX.getKey().xj.equals(N[j].id)){
				exp.addTerm(1, varX.getValue());
				
			}
		model.addEq(exp, 1, "Constr_(2)_for_customer_"+N[j].id);
		conta_vincoli++;		  
	}
}

public void add_constraint_3_() throws IloException {

	for(int i=0;i<N.length;i++) {
		IloLinearNumExpr exp= model.linearNumExpr();
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet())
			if(varX.getKey().xi.equals(N[i].id)){
				exp.addTerm(1, varX.getValue());
			}
		model.addEq(exp, 1, "Constr_(3)_for_customer_"+N[i].id);
		conta_vincoli++;		  
	}
}

public void add_constraint_4_() throws IloException {
	for (int i=0;i<Dep.length;i++) {
		IloLinearNumExpr exp= model.linearNumExpr();
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet())
			if(varX.getKey().xi.equals(Dep[i].id)){
				exp.addTerm(1, varX.getValue());
			}
		model.addLe(exp, 1, "Constr_(4)_for_depot_"+Dep[i].id);
		conta_vincoli++;
	}
}

public void add_constraint_5_() throws IloException {
	for (int i=0;i<Dep.length;i++) {
		IloLinearNumExpr exp= model.linearNumExpr();
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet())
			if(varX.getKey().xi.equals(Dep[i].id)){
				exp.addTerm(1, varX.getValue());
			}
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet())
			if(varX.getKey().xj.equals(Dep[i].id)){
				exp.addTerm(-1, varX.getValue());
			}
		model.addEq(exp, 0, "Constr_(5)_for_depot_"+Dep[i].id);
		conta_vincoli++;
	}
}

public void add_constraint_6_() throws IloException {
	for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		if(varX.getKey().staz.equals("fictius")) {
			for (int p = 0; p < P.length; p++) {
				//System.err.println(varX.getKey().xi+"----"+varX.getKey().xj);
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, t.get(varX.getKey()));
				exp.addTerm(-60 * D.get(varX.getKey().xi).get(varX.getKey().xj) * kp[p], v.get(varX.getKey()));
				//System.err.println(varX.getKey().xi+"--"+varX.getKey().xj);
				double bigM = 60 * D.get(varX.getKey().xi).get(varX.getKey().xj) * (Math.max(kp[0]*v_min.get(new IJind(varX.getKey().xi,varX.getKey().xj)),0))+
						60 * D.get(varX.getKey().xi).get(varX.getKey().xj)*bp[0];
				exp.addTerm(-bigM, varX.getValue());

				model.addGe(exp, 60 * D.get(varX.getKey().xi).get(varX.getKey().xj) * bp[p]-bigM,"Constr_(6)_for_pair_" + varX.getKey().xi + "_" + varX.getKey().xj+"_"+p);
				conta_vincoli++;
			}
		}
	}

}

public void add_constraint_7_() throws IloException {

	for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		if (!varX.getKey().staz.equals("fictius")) {
			for (int p = 0; p < P.length; p++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, ta.get(varX.getKey()));
				exp.addTerm(-60 * D.get(varX.getKey().xi).get(varX.getKey().staz) * kp[p], va.get(varX.getKey()));
				double bigM = 60 * D.get(varX.getKey().xi).get(varX.getKey().staz) * (Math.max(kp[0]*v_min.get(new IJind(varX.getKey().xi,varX.getKey().staz)),0))+
						60 * D.get(varX.getKey().xi).get(varX.getKey().staz)*bp[0];

				exp.addTerm(-bigM, varX.getValue());

				model.addGe(exp, 60 * D.get(varX.getKey().xi).get(varX.getKey().staz) * bp[p] -bigM,
						"Constr_(7)_for_pairA_" + varX.getKey().xi + "_" + varX.getKey().xj + "_station_" + varX.getKey().staz + "_" + p);
				conta_vincoli++;
			}
		}
	}
}

public void add_constraint_8_() throws IloException {

	for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		if(!varX.getKey().staz.equals("fictius")) {
			for (int p = 0; p < P.length; p++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp = model.linearNumExpr();
				exp.addTerm(1, tb.get(varX.getKey()));
				exp.addTerm(-60 * D.get(varX.getKey().staz).get(varX.getKey().xj) * kp[p], vb.get(varX.getKey()));
				double bigM = 60 * D.get(varX.getKey().staz).get(varX.getKey().xj) * (Math.max(kp[0]*v_min.get(new IJind(varX.getKey().staz,varX.getKey().xj)),0))+
						60 * D.get(varX.getKey().staz).get(varX.getKey().xj)*bp[0];

				exp.addTerm(-bigM, varX.getValue());

				model.addGe(exp,60 * D.get(varX.getKey().staz).get(varX.getKey().xj) * bp[p]	-bigM,
						"Constr_(8)_for_pairB_" + varX.getKey().xi + "_" + varX.getKey().xj+"_station_"+varX.getKey().staz+"_"+p);
				conta_vincoli++;
			}
		}
	}
}

public void add_constraint_9_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, b.get(varX.getKey()));
			exp.addTerm(-1/speed_min, x.get(varX.getKey()));
			for(RechargeTypes st:RRT.get(varX.getKey().staz)) {
				for(IJSRIndex ijsr:beta.keySet()) {
					if(ijsr.xi.equals(varX.getKey().xi) && ijsr.xj.equals(varX.getKey().xj) && ijsr.staz.equals(varX.getKey().staz) && ijsr.tec==st.id)
						exp.addTerm(-1/st.speed, beta.get(ijsr));
				}
				
			}
			model.addGe(exp, -1/speed_min, "Constr_(9)_for_pair_" + varX.getKey().xi + "_" + varX.getKey().xj + "_station_" + varX.getKey().staz);
			conta_vincoli++;
		}
	}
}
public void add_constraint_10_() throws IloException {
	
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (varX.getKey().staz.equals("fictius")&&varX.getKey().xi.contains("D")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, a.get(varX.getKey().xj));
			exp.addTerm(-1, t.get(new XIndex(varX.getKey().xi, varX.getKey().xj, "fictius")));
			double bigM = (NodesMap.get("D0").s + 60*D.get("D0").get(varX.getKey().xj))/v_min.get(new IJind(varX.getKey().xi,varX.getKey().xj));
			exp.addTerm(-bigM, x.get(new XIndex(varX.getKey().xi, varX.getKey().xj, "fictius")));
			model.addGe(exp, -bigM, "Constr_(10)_for_pair_" + varX.getKey().xi+"--"+varX.getKey().xj);
			conta_vincoli++;
		
		}
	}

}
public void add_constraint_11_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (varX.getKey().xi.contains("D")  && !varX.getKey().staz.equals("fictius")) {
		//	System.err.println("nodo:"+varX.getKey().xi+"--"+"nodo"+varX.getKey().xj+" valore di s"+NodesMap.get(varX.getKey().xi).s);
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, a.get(varX.getKey().xj));
			exp.addTerm(-1, ta.get(varX.getKey()));
			exp.addTerm(-1, tb.get(varX.getKey()));
			exp.addTerm(-1, b.get(varX.getKey()));
			double bigM = (NodesMap.get(varX.getKey().xi).s + 60*D.get(varX.getKey().xi).get(varX.getKey().staz)/v_min.get(new IJind(varX.getKey().xi,varX.getKey().staz)) +
					60*D.get(varX.getKey().staz).get(varX.getKey().xj)/v_min.get(new IJind(varX.getKey().staz,varX.getKey().xj)) + 1/speed_min);

			exp.addTerm(-bigM, varX.getValue());

			model.addGe(exp, -bigM, "Constr_(11)_for_pair_" + varX.getKey().xi + "_" + varX.getKey().xj + "_station_" + varX.getKey().staz);
			conta_vincoli++;
		}
	}
}
public void add_constraint_12a_() throws IloException {
	for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		if(!varX.getKey().xi.contains("D") && varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, a.get(varX.getKey().xj));
			exp.addTerm(-1, a.get(varX.getKey().xi));
			exp.addTerm(-1, t.get(varX.getKey()));
			exp.addTerm(-1, w.get(varX.getKey().xi));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(-bigM, varX.getValue());

			model.addGe(exp, NodesMap.get(varX.getKey().xi).service_time -bigM, "Constr_(12a)_for_pair_"+varX.getKey().xi+"_"+varX.getKey().xj);
			conta_vincoli++;
		}
	}
}

public void add_constraint_12b_() throws IloException {
	for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		if(!varX.getKey().xi.contains("D")  && varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, a.get(varX.getKey().xj));
			exp.addTerm(-1, a.get(varX.getKey().xi));
			exp.addTerm(-1, t.get(varX.getKey()));
			exp.addTerm(-1, w.get(varX.getKey().xi));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(bigM, varX.getValue());

			model.addLe(exp,NodesMap.get(varX.getKey().xi).service_time+bigM, "Constr_(12b)_for_pair_"+varX.getKey().xi+"_"+varX.getKey().xj);
			conta_vincoli++;
		}
	}
}
public void add_constraint_13a_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().xi.contains("D")  && !varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, a.get(varX.getKey().xj));
			exp.addTerm(-1, a.get(varX.getKey().xi));
			exp.addTerm(-1, ta.get(varX.getKey()));
			exp.addTerm(-1, tb.get(varX.getKey()));
			exp.addTerm(-1, w.get(varX.getKey().xi));
			exp.addTerm(-1, b.get(varX.getKey()));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(-bigM, varX.getValue());

			model.addGe(exp, NodesMap.get(varX.getKey().xi).service_time - bigM, "Constr_(13a)_for_pair_" + varX.getKey().xi + "_" + varX.getKey().xj + "_station_" + varX.getKey().staz);
			conta_vincoli++;
		}
	}
}
public void add_constraint_13b_() throws IloException {
	for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		if(!varX.getKey().xi.contains("D")  && !varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, a.get(varX.getKey().xj));
			exp.addTerm(-1, a.get(varX.getKey().xi));
			exp.addTerm(-1, ta.get(varX.getKey()));
			exp.addTerm(-1, tb.get(varX.getKey()));
			exp.addTerm(-1, w.get(varX.getKey().xi));
			exp.addTerm(-1, b.get(varX.getKey()));
			double bigM = NodesMap.get("D0").e;

			exp.addTerm(bigM, varX.getValue());

			model.addLe(exp,NodesMap.get(varX.getKey().xi).service_time+bigM, "Constr_(13b)_for_pair_"+varX.getKey().xi+"_"+varX.getKey().xj+"_station_"+varX.getKey().staz);
			conta_vincoli++;
		}
	}
}
public void add_constraint_14_() throws IloException {
	for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		if(varX.getKey().staz.equals("fictius")) {
			for(int q=0;q<Q.length;q++) {
				IloLinearNumExpr exp= model.linearNumExpr();
				IJind ij = new IJind(varX.getKey().xi, varX.getKey().xj);
				exp.addTerm(1, e.get(varX.getKey()));
				exp.addTerm(-kq[q], v.get(varX.getKey()));
				exp.addTerm(-phi, f.get(ij));
				exp.addTerm(-1, varX.getValue());
				model.addGe(exp, bq[q]-1, "Constr_(14)_for_pair_"+ij.xi+"_"+ij.xj);
				conta_vincoli++;
			}
		}
	}
}
public void add_constraint_15_() throws IloException {
	for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		if (!varX.getKey().staz.equals("fictius")) {
			for (int q = 0; q < Q.length; q++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				IJind ij = new IJind(varX.getKey().xi, varX.getKey().xj);
				exp.addTerm(1, ea.get(varX.getKey()));
				exp.addTerm(-kq[q], va.get(varX.getKey()));
				exp.addTerm(-phi, f.get(ij));
				exp.addTerm(-1, varX.getValue());
				model.addGe(exp, bq[q] - 1, "Constr_(15)_for_pair_" + ij.xi + "_" + ij.xj + "_station_" + varX.getKey().staz);
				conta_vincoli++;
			}
		}
	}
}
public void add_constraint_16_() throws IloException {
	for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
		if (!varX.getKey().staz.equals("fictius")) {
			for (int q = 0; q < Q.length; q++) {
				IloLinearNumExpr exp = model.linearNumExpr();
				IJind ij = new IJind(varX.getKey().xi, varX.getKey().xj);
				exp.addTerm(1, eb.get(varX.getKey()));
				exp.addTerm(-kq[q], vb.get(varX.getKey()));
				exp.addTerm(-phi, f.get(ij));
				exp.addTerm(-1, varX.getValue());
				model.addGe(exp, bq[q] - 1, "Constr_(16)_for_pair_" + ij.xi + "_" + ij.xj + "_station_" + varX.getKey().staz);
				conta_vincoli++;
			}
		}
	}
}
public void add_constraint_17_() throws IloException {
	for(int v=0;v<Dep.length;v++) {
		IloLinearNumExpr exp = model.linearNumExpr();
		exp.addTerm(1, r_v_0.get(Dep[v].id));
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
			if(varX.getKey().xi.equals(Dep[v].id)) {
				exp.addTerm(-sigma, varX.getValue());
			}
		}
		model.addGe(exp, 0, "Constr_(17)_for_depot_" +Dep[v].id );
		conta_vincoli++;
		IloLinearNumExpr exp1 = model.linearNumExpr();
		exp1.addTerm(1, r_v_0.get(Dep[v].id));
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
			if(varX.getKey().xi.equals(Dep[v].id)) {
				exp1.addTerm(-gamma, varX.getValue());
			}
		}
		model.addLe(exp1, 0, "Constr_(17)_for_depot_" +Dep[v].id );
		conta_vincoli++;
	}
}
public void add_constraint_18_() throws IloException {
	for(int v=0;v<Dep.length;v++) {
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
			if(varX.getKey().xi.equals(Dep[v].id)&&varX.getKey().staz.equals("fictius")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r.get(varX.getKey().xj));
				exp.addTerm(-1, r_v_0.get(varX.getKey().xi));
				exp.addTerm(D.get(varX.getKey().xi).get(varX.getKey().xj), e.get(varX.getKey()));
				exp.addTerm(-1, varX.getValue());
				model.addGe(exp, - 1, "Constr_(18)_for_pair_" +varX.getKey().xi+"--"+varX.getKey().xj );
				conta_vincoli++;
				
			}
		}
	}
}
public void add_constraint_19_() throws IloException {
	for(int v=0;v<Dep.length;v++) {
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
			if(varX.getKey().xi.equals(Dep[v].id)&&!varX.getKey().staz.equals("fictius")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r_v_0.get(varX.getKey().xi));
				exp.addTerm(-D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
				exp.addTerm(-1, varX.getValue());
				model.addGe(exp, - 1, "Constr_(19)_for_" +varX.getKey().xi+"--"+varX.getKey().xj+"--"+varX.getKey().staz);
				conta_vincoli++;
				
			}
		}
	}
}
public void add_constraint_20_() throws IloException {
	for(int v=0;v<Dep.length;v++) {
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
			if(varX.getKey().xi.equals(Dep[v].id)&&!varX.getKey().staz.equals("fictius")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r.get(varX.getKey().xj));
				exp.addTerm(-1, r_v_0.get(varX.getKey().xi));
				exp.addTerm(D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
				exp.addTerm(D.get(varX.getKey().staz).get(varX.getKey().xj), eb.get(varX.getKey()));
				exp.addTerm(1, varX.getValue());
				for(RechargeTypes rt:RRT.get(varX.getKey().staz))
					for(IJSRIndex ijsr:IJSRindici) {
						if(ijsr.xi.equals(varX.getKey().xi)&&ijsr.xj.equals(varX.getKey().xj)&&ijsr.staz.equals(varX.getKey().staz)&&
								ijsr.tec==rt.id) {
							exp.addTerm(-1, beta.get(ijsr));
						}
					}
				model.addLe(exp, 1, "Constr_(20)_for_" +varX.getKey().xi+"--"+varX.getKey().xj+"--"+varX.getKey().staz);
				conta_vincoli++;
				IloLinearNumExpr exp1 = model.linearNumExpr();
				exp1.addTerm(1, r_v_0.get(varX.getKey().xi));
				exp1.addTerm(-D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
				exp1.addTerm(-D.get(varX.getKey().staz).get(varX.getKey().xj), eb.get(varX.getKey()));
				exp1.addTerm(-1, varX.getValue());
				for(RechargeTypes rt:RRT.get(varX.getKey().staz))
					for(IJSRIndex ijsr:IJSRindici) {
						if(ijsr.xi.equals(varX.getKey().xi)&&ijsr.xj.equals(varX.getKey().xj)&&ijsr.staz.equals(varX.getKey().staz)&&
								ijsr.tec==rt.id) {
							exp1.addTerm(1, beta.get(ijsr));
						}
					}
				model.addLe(exp1, gamma-1, "Constr_(20)_for_" +varX.getKey().xi+"--"+varX.getKey().xj+"--"+varX.getKey().staz);
				conta_vincoli++;
			}
		}
	}
}
public void add_constraint_21_() throws IloException {
	for(int v=0;v<Dep.length;v++) {
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
			if(varX.getKey().xi.equals(Dep[v].id)&&!varX.getKey().staz.equals("fictius")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r_v_0.get(varX.getKey().xi));
				exp.addTerm(-D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
				exp.addTerm(-D.get(varX.getKey().staz).get(varX.getKey().xj), eb.get(varX.getKey()));
				exp.addTerm(-1, varX.getValue());
				model.addGe(exp, sigma-1, "Constr_(21)_for_" +varX.getKey().xi+"--"+varX.getKey().xj+"--"+varX.getKey().staz);
				conta_vincoli++;
				
			}
		}
	}
}
public void add_constraint_22_() throws IloException {
	for(int v=0;v<Dep.length;v++) {
		for(Map.Entry<XIndex,IloNumVar> varX: x.entrySet()) {
			if(varX.getKey().xi.equals(Dep[v].id)&&!varX.getKey().staz.equals("fictius")) {
				IloLinearNumExpr exp = model.linearNumExpr();
				exp.addTerm(1, r_v_0.get(varX.getKey().xi));
				exp.addTerm(-D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
				exp.addTerm(-D.get(varX.getKey().staz).get(varX.getKey().xj), eb.get(varX.getKey()));
				exp.addTerm(1, varX.getValue());
				for(RechargeTypes rt:RRT.get(varX.getKey().staz))
					for(IJSRIndex ijsr:IJSRindici) {
						if(ijsr.xi.equals(varX.getKey().xi)&&ijsr.xj.equals(varX.getKey().xj)&&ijsr.staz.equals(varX.getKey().staz)&&
								ijsr.tec==rt.id) {
							exp.addTerm(1, beta.get(ijsr));
						}
					}
				model.addLe(exp, gamma+1, "Constr_(22)_for_" +varX.getKey().xi+"--"+varX.getKey().xj+"--"+varX.getKey().staz);
				conta_vincoli++;
				
			}
		}
	}
}
public void add_constraint_23a_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().xi.contains("D") && !varX.getKey().xj.contains("D")&& varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(varX.getKey().xj));
			exp.addTerm(-1, r.get(varX.getKey().xi));
			exp.addTerm(D.get(varX.getKey().xi).get(varX.getKey().xj), e.get(varX.getKey()));
			exp.addTerm(1, varX.getValue());
			model.addLe(exp, 1, "Constr_(23a)_for_pair_" + varX.getKey().xi + "_" + varX.getKey().xj);
			conta_vincoli++;
		}
	}
}
public void add_constraint_23b_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().xi.contains("D") && !varX.getKey().xj.contains("D")&& varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(varX.getKey().xj));
			exp.addTerm(-1, r.get(varX.getKey().xi));
			exp.addTerm(D.get(varX.getKey().xi).get(varX.getKey().xj), e.get(varX.getKey()));
			exp.addTerm(-1, varX.getValue());
			model.addGe(exp, -1, "Constr_(23b)_for_pair_" + varX.getKey().xi + "_" + varX.getKey().xj);
			conta_vincoli++;

		}
	}
}
public void add_constraint_24_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().xi.contains("D") && !varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(varX.getKey().xi));
			exp.addTerm(-D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
			exp.addTerm(-1, varX.getValue());
			model.addGe(exp, -1, "Constr_(24)_for_pair_"+ varX.getKey().xi+"_"+varX.getKey().xj+"_station_"+varX.getKey().staz);
			conta_vincoli++;
		}
	}
}
public void add_constraint_25a_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().xi.contains("D") && !varX.getKey().xj.contains("D")&&!varX.getKey().staz.equals("fictius")) {
			//System.err.println(varX.getKey().xi+"------>"+varX.getKey().xj);
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(varX.getKey().xj));
			exp.addTerm(-1, r.get(varX.getKey().xi));
			exp.addTerm(D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
			exp.addTerm(D.get(varX.getKey().staz).get(varX.getKey().xj), eb.get(varX.getKey()));
			exp.addTerm(1, varX.getValue());
			for(RechargeTypes rt:RRT.get(varX.getKey().staz))
				for(IJSRIndex ijsr:IJSRindici) {
					if(ijsr.xi.equals(varX.getKey().xi)&&ijsr.xj.equals(varX.getKey().xj)&&ijsr.staz.equals(varX.getKey().staz)&&
							ijsr.tec==rt.id) {
						exp.addTerm(-1, beta.get(ijsr));
					}
				}
			//System.err.println(exp);
			model.addLe(exp, 1, "Constr_(25a)_for_pair_"+ varX.getKey().xi+"_"+varX.getKey().xj+"_station_"+varX.getKey().staz);
			conta_vincoli++;
		}
	}
}
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
}
public void add_constraint_26_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().xi.contains("D") && !varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(varX.getKey().xi));
			exp.addTerm(-D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
			exp.addTerm(-D.get(varX.getKey().staz).get(varX.getKey().xj), eb.get(varX.getKey()));
			exp.addTerm(-1, varX.getValue());
			model.addGe(exp, sigma-1, "Constr_(26)_for_pair_"+ varX.getKey().xi+"_"+varX.getKey().xj+"_station_"+varX.getKey().staz);
			conta_vincoli++;
		}
	}
}
public void add_constraint_27_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().xi.contains("D") && !varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			exp.addTerm(1, r.get(varX.getKey().xi));
			exp.addTerm(-D.get(varX.getKey().xi).get(varX.getKey().staz), ea.get(varX.getKey()));
			exp.addTerm(-D.get(varX.getKey().staz).get(varX.getKey().xj), eb.get(varX.getKey()));
			for(RechargeTypes rt:RRT.get(varX.getKey().staz))
				for(IJSRIndex ijsr:IJSRindici) {
					if(ijsr.xi.equals(varX.getKey().xi)&&ijsr.xj.equals(varX.getKey().xj)&&ijsr.staz.equals(varX.getKey().staz)&&
							ijsr.tec==rt.id) {
						exp.addTerm(1, beta.get(ijsr));
					}
				}
			exp.addTerm(1, varX.getValue());
			model.addLe(exp, gamma+1, "Constr_(27)_for_pair_"+ varX.getKey().xi+"_"+varX.getKey().xj+"_station_"+varX.getKey().staz);
			conta_vincoli++;
		}
	}
}
public void add_constraint_28_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			IJind ij = new IJind(varX.getKey().xi, varX.getKey().xj);
			exp.addTerm(1, v.get(varX.getKey()));
			exp.addTerm(-v_max.get(ij), varX.getValue());
			model.addLe(exp, 0, "Constr_(28)_for_pair_" + ij.xi + "_" + ij.xj);
			IloLinearNumExpr exp1 = model.linearNumExpr();
			exp1.addTerm(1, v.get(varX.getKey()));
			exp1.addTerm(-v_min.get(ij), varX.getValue());
			model.addGe(exp1, 0, "Constr_(28)_for_pair_" + ij.xi + "_" + ij.xj);
			conta_vincoli += 2;
		}
	}
}
public void add_constraint_29_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1= model.linearNumExpr();
			exp.addTerm(1, va.get(varX.getKey()));
			exp1.addTerm(1, va.get(varX.getKey()));
			exp.addTerm(-v_max.get(new IJind(varX.getKey().xi,varX.getKey().staz)), varX.getValue());
			exp1.addTerm(-v_min.get(new IJind(varX.getKey().xi,varX.getKey().staz)), varX.getValue());
			model.addLe(exp, 0, "Constr_(29)_for_customer_"+ varX.getKey().xi+"_station_"+ varX.getKey().staz);
			model.addGe(exp1, 0, "Constr_(29)_for_customer_"+ varX.getKey().xi+"_station_"+ varX.getKey().staz);
			conta_vincoli += 2;
		}
	}
}
public void add_constraint_30_() throws IloException {
	for (Map.Entry<XIndex, IloNumVar> varX : x.entrySet()) {
		if (!varX.getKey().staz.equals("fictius")) {
			IloLinearNumExpr exp = model.linearNumExpr();
			IloLinearNumExpr exp1= model.linearNumExpr();
			exp.addTerm(1, vb.get(varX.getKey()));
			exp1.addTerm(1, vb.get(varX.getKey()));
			exp.addTerm(-v_max.get(new IJind(varX.getKey().staz,varX.getKey().xj)), varX.getValue());
			exp1.addTerm(-v_min.get(new IJind(varX.getKey().staz,varX.getKey().xj)), varX.getValue());
			model.addLe(exp, 0, "Constr_(30)_for_customer_"+ varX.getKey().staz+"_station_"+ varX.getKey().xj);
			model.addGe(exp1, 0, "Constr_(30)_for_customer_"+ varX.getKey().staz+"_station_"+ varX.getKey().xj);
			conta_vincoli += 2;
		}
	}
}
public void add_constraint_31_() throws IloException {
	for(int i=1;i<N.length;i++) {
		IloLinearNumExpr exp= model.linearNumExpr();
		for(IJind find: Findici) {
			if(find.xj.equals(N[i].id))
				exp.addTerm(1, f.get(find));
		}
		for(IJind find: Findici) {
			if(find.xi.equals(N[i].id))
				exp.addTerm(-1, f.get(find));
		}
		model.addEq(exp, N[i].demand, "Constr_(31)_for_customer_"+N[i].id);
		conta_vincoli++;		  
	}
}
public void add_constraint_32_() throws IloException {
	for(Map.Entry<IJind,IloNumVar> varF: f.entrySet()){
			IloLinearNumExpr exp= model.linearNumExpr();
			exp.addTerm(1, f.get(varF.getKey()));

			for (XIndex xKey : Xindici) {
				if (xKey.xi.equals(varF.getKey().xi) && xKey.xj.equals(varF.getKey().xj)) {
					exp.addTerm(-Cl, x.get(xKey));
				}
			}
			model.addLe(exp, 0, "Constr_(32)_for_pair_"+varF.getKey().xi+"_"+varF.getKey().xj);
			conta_vincoli++;
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
	for(int i=0; i<ir.NodeId.size();i++)
	{	if(!ir.NodeId.get(i).contains("S")) {
		 node n=new node();
		 n.id=ir.NodeId.get(i);
		 n.type=ir.Type.get(i);
		 n.demand=ir.Demand.get(i);
		 n.s=ir.ReadyTime.get(i);
		 n.e=ir.DueDate.get(i);
		 n.service_time=ir.ServiceTime.get(i);
		 n.x=ir.XCoord.get(i);
		 n.y=ir.YCoord.get(i);
		 if(!ir.NodeId.get(i).contains("D")) {
			 N[j]=n;
			 N0[j0]=n;
			// NodeSImap.put(n.id,j);
			 NodesMap.put(n.id, n);
			 j++;
			 j0++;
		 }
		 
		 
		 int ind=0;
		 if(ir.NodeId.get(i).contains("D")) {
			 Dep[ind]=n;
			 ListOfDeps.add(ir.NodeId.get(i));
			 N0[j0]=n;
			 j0++;
			// NodeSImap.put(n.id,j);
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
				 j0++;
				// NodeSImap.put(n.id,j);
				 NodesMap.put(n1.id, n1);
				  ListOfDeps.add( n1.id);
				 }
		 }
		 
	}
	else {
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

public void write_output(String args) throws FileNotFoundException, IloException {
	String file_name=args.replace(".txt", "");
	pr=new PrintStream(new File(file_name+"_output.txt"));
	pr_excel=new PrintStream(new File(file_name+"_excel.csv"));

	pr.println("number of 0-1 variables:"+conta_variabili_int);
	pr.println("number of continous variables:"+conta_variabili_cont);
	pr.println("number of constraints:"+conta_vincoli);																
	pr.println("objective function without service times at customers:"+model.getObjValue());
	double obj=model.getObjValue();
	double objcompleto = obj;
	for(int i=0;i<N.length;i++)
		objcompleto+=FD*N[i].service_time;

	pr_excel.println("Obj;"+obj+";Completo;"+objcompleto+";gap;"+ model.getMIPRelativeGap());
	pr_excel.println("vehicle;node;node;ebattery;time;speed;demand;dist;service time;ready;due;b;resbatt;arr time;w");

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

	if(ListOfDeps.contains(N0[xi].id))
		ListOfDeps.remove(N0[xi].id);

	boolean firstCustFound = false;
	while(Node2Visit>=0) {
		if(!FoundSucc && checkDep) {
			currNode = ListOfDeps.get(0);
			xi=0;
			while (!N0[xi].id.contains(currNode))
				xi++;
			ListOfDeps.remove(0);
		}

		FoundSucc = false;
		for (int j = 0; j < N0.length; j++) {
			if (xi != j && S_ij.containsKey(N0[xi].id) && S_ij.get(N0[xi].id).containsKey(N0[j].id)) {
				for (int s = 0; s < S_ij.get(N0[xi].id).get(N0[j].id).size(); s++) {
					
					if (xmap.containsKey(N0[xi].id) && xmap.get(N0[xi].id).containsKey(N0[j].id) )
						continue; // già usato

					if (model.getValue(x.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) >= 0.999) {
						checkDep = false;
						// il succ è customer
						if (S_ij.get(N0[xi].id).get(N0[j].id).get(s).equals("fictius")) {
							pr_excel.print(numeroVeicolo + ";" + N0[xi].id + ";" + N0[j].id + ";" +
									model.getValue(e.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									model.getValue(t.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									model.getValue(v.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									N0[j].demand + ";" + D.get(N0[xi].id).get(N0[j].id) + ";" + N0[j].service_time + ";" + N0[j].s + ";" + N0[j].e + ";0;"
							);
							if (!N0[j].id.contains("D"))
								pr_excel.println(model.getValue(r.get(N0[j].id)) + ";" + model.getValue(a.get(N0[j].id)) + ";" + model.getValue(w.get(N0[j].id)));
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
							if (Node2Visit>=0 && currNode.contains("D") && ListOfDeps.size()>0) {
								Node2Visit++;
								numeroVeicolo++;
								currNode = ListOfDeps.get(0);
								xi=0;
								while (!N0[xi].id.contains(currNode))
									xi++;
								ListOfDeps.remove(0);
								checkDep=true;
							}
							break;
						} else if (!S_ij.get(N0[xi].id).get(N0[j].id).get(s).equals("fictius")) {
							NumeroSosteRS++;
							pr_excel.print(numeroVeicolo + ";" + N0[xi].id + ";" + S_ij.get(N0[xi].id).get(N0[j].id).get(s) + ";" +
									model.getValue(ea.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									model.getValue(ta.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s))))+ ";" +
									model.getValue(va.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s)))) + ";" +
									"0" + ";" + D.get(N0[xi].id).get(S_ij.get(N0[xi].id).get(N0[j].id).get(s)) + ";" +
									"0" + ";" + RS[StationMap.get(S_ij.get(N0[xi].id).get(N0[j].id).get(s))].s + ";" + RS[StationMap.get(S_ij.get(N0[xi].id).get(N0[j].id).get(s))].e +
									";"+model.getValue(b.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s))))+";");
							if (!N0[j].id.contains("D")) {
								if (!N0[xi].id.contains("D"))
									pr_excel.println(gamma + ";" + (model.getValue(a.get(N0[xi].id)) + N0[xi].service_time + model.getValue(w.get(N0[xi].id)) +
											model.getValue(ta.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s))))) +
											";" + model.getValue(w.get(N0[j].id)));
								else
									pr_excel.println(gamma + ";" + (model.getValue(ta.get(new XIndex(N0[xi].id,N0[j].id,S_ij.get(N0[xi].id).get(N0[j].id).get(s))))) +
											";" + model.getValue(w.get(N0[j].id)));
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
									D.get(S_ij.get(N0[xi].id).get(N0[j].id).get(s)).get(N0[j].id) +";"+ N0[j].service_time + ";" + N0[j].s + ";" + N0[j].e + ";0;");
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

							if (Node2Visit>=0 && currNode.contains("D") && ListOfDeps.size()>0) {
								Node2Visit++;
								numeroVeicolo++;
								currNode = ListOfDeps.get(0);
								xi=0;
								while (!N0[xi].id.contains(currNode))
									xi++;
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
		if(j!=0&&S_ij.containsKey(no.id)&&S_ij.get(no.id).containsKey(N[j].id)) {
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
		outpr.println(file_name + ";" + model.getStatus() + ";" + model.getObjValue() + ";" + count_veic + ";" + model.getValue(CostoVeicoli) + ";" + model.getValue(CostoEnergia) + ";" +
				model.getValue(CostoDrivers) + ";" + total_cplex_time + ";" + model.getMIPRelativeGap()+";CLNLS");
		outpr.close();
	} catch (IOException e) {
		System.err.println("Errore scrittura file RiassResult.csv: "+e);
	}

pr.close();

	PrintStream soste = new PrintStream(new FileOutputStream("Soste.csv", true));
	soste.println(file_name+";"+model.getObjValue()+";"+NumeroSosteRS);
	soste.close();

}

public static void main(String[] args) throws  IOException, IloException {

	InstanceReaderGeneratorTech ir = new InstanceReaderGeneratorTech();
	ir.generate(args);
	System.out.println("Cloneless");
	cloneless_tech jc=new cloneless_tech();
	jc.num_veic=Integer.parseInt(args[5]);
	if(args[2].contains("01"))
		jc.epsilon=0.1;
	else
		jc.epsilon=1.0;
	timeLimit = Double.parseDouble(args[6]);
	jc.rho = Double.parseDouble(args[7]);

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
	jc.generate_nodes(ir);
	jc.generate_distances();
	jc.init_parameters(ir);
	jc.compute_S();
	jc.initVariables();
	jc.initModel();
	//jc.model.exportModel("model_tech_red.lp");

	jc.solve(args[0]);

	jc.write_output(args[1]);

}
}
