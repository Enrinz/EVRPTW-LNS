import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;
import java.lang.reflect.Field;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class matheuristic_k_degree_hybridOneGenerator_tech {

int k, k_prime;
public cloneless_tech2 M;

public PrintStream pr=null;
public HashMap<String,ArrayList<String>> arcs_in=new HashMap<String,ArrayList<String>>();
public HashMap<String,ArrayList<String>> arcs_out=new HashMap<String,ArrayList<String>>();

public HashMap<String, Integer> degree_ingoing=new HashMap<String, Integer>();
public HashMap<String, Integer> degree_outgoing=new HashMap<String, Integer>();

public HashMap<String, Integer> degree_in=new HashMap<String, Integer>();
public HashMap<String, Integer> degree_out=new HashMap<String, Integer>();

public HashMap<String, Integer> degree_ingoing_aus=new HashMap<String, Integer>();
public HashMap<String, Integer> degree_outgoing_aus=new HashMap<String, Integer>();

public HashMap<String, ArrayList<customer>> prob_ingoing=new HashMap<String, ArrayList<customer>>();
public HashMap<String, ArrayList<customer>> prob_outgoing=new HashMap<String, ArrayList<customer>>();

public HashMap<String, ArrayList<customer>> prob_ingoing_aus=new HashMap<String, ArrayList<customer>>();
public HashMap<String, ArrayList<customer>> prob_outgoing_aus=new HashMap<String, ArrayList<customer>>();

public HashMap<String, ArrayList<customer>> prob_ingoing_aus_1=new HashMap<String, ArrayList<customer>>();
public HashMap<String, ArrayList<customer>> prob_outgoing_aus_1=new HashMap<String, ArrayList<customer>>();

LinkedHashMap<String, Integer> sortedIngoingDegree = new LinkedHashMap<>();
LinkedHashMap<String, Integer> sortedOutgoingDegree = new LinkedHashMap<>();

public HashMap<String, ArrayList<String>> closest_ingoing=new HashMap<String, ArrayList<String>>();
public HashMap<String, ArrayList<String>> closest_outgoing=new HashMap<String, ArrayList<String>>();

ArrayList<String> vector_in=new ArrayList<String> ();
ArrayList<String> vector_out=new ArrayList<String> ();

public HashMap<String, ArrayList<String>> random_ingoing=new HashMap<String, ArrayList<String>>();
public HashMap<String, ArrayList<String>> random_outgoing=new HashMap<String, ArrayList<String>>();
//Random gen_ingoing, gen_outgoing;
Random gen_ingoing;
Long seed;

int num_infeasibility, num_optimal, num_run;

public ArrayList<XIndex> ZeroedXvar = new ArrayList<XIndex>();

public matheuristic_k_degree_hybridOneGenerator_tech(String[] args,long seed) throws IOException {

	M=new cloneless_tech2();

	M.num_veic=Integer.parseInt(args[5]);
	if(args[2].contains("01"))
		M.epsilon=0.1;
	else
		M.epsilon=1.0;
	//M.rho = Double.parseDouble(args[6]);
	k = Integer.parseInt(args[7]);
	k_prime = Integer.parseInt(args[8]);
	//num_infeasibility = Integer.parseInt(args[10]);
	//num_optimal = Integer.parseInt(args[11]);
	//num_run = Integer.parseInt(args[12]);
	M.init(args[0]);
	String name=args[1].replace(".txt", "");
	pr=new PrintStream(new File(name+"_output_k_degree_matheuristic"+k_prime+".txt"));
	//long seed = 0;
	if(seed!=0) {//Long.parseLong(args[9])
		//seed=Long.parseLong(args[9]);
		pr.println("Seed: "+seed);
		gen_ingoing=new Random(seed);
	}
	else {
		gen_ingoing=new Random();
		seed = gen_ingoing.nextLong();
		pr.println("Seed: "+seed);
		//gen_ingoing.setSeed(seed);
	}
}

public void AzzeraVarX() throws  IloException{
	for(XIndex xind: ZeroedXvar){
			M.x.get(xind).setUB(0.0);
		}
}

/*
public ArrayList<String> find_customer_feasible(int id, String last)
{ArrayList<String> customer_feasible=new ArrayList<String>();
ArrayList<customer> cust=new ArrayList<customer>();
System.err.println("first:"+M.N[0].id);
for(int i=1;i<M.N.length;i++) {
	if(M.N[i].id.equals(M.N[id].id))continue;
	if(M.N[i].demand+M.N[id].demand>M.Cl)continue;
	if(M.N[id].s+M.N[id].service_time+M.D.get(M.N[id].id).get(M.N[i].id)/M.v_max.get(new IJind(M.N[id].id,M.N[i].id))>M.N[i].e)continue;
	if(M.D.get(M.N[0].id).get(M.N[id].id)/M.v_max.get(new IJind(M.N[0].id,M.N[id].id))+M.D.get(M.N[id].id).get(M.N[i].id)/M.v_max.get(new IJind(M.N[id].id,M.N[i].id))+
			M.D.get(M.N[i].id).get(M.N[0].id)/M.v_max.get(new IJind(M.N[i].id,M.N[0].id))>M.N[0].e)continue;
	customer c=new customer();
	c.id=M.N[i].id;
	c.dist=M.D.get(M.N[id].id).get(M.N[i].id);
	cust.add(c);
	customer_feasible.add(M.N[i].id);
}
if(cust.size()==k)return customer_feasible;
Collections.sort(cust);
customer_feasible.clear();
double dist=Double.NEGATIVE_INFINITY;
for(int i=0;i<cust.size();i++)
{
	customer_feasible.add(cust.get(i).id);
	if(M.D.get(M.N[id].id).get(cust.get(i).id)>dist) {
		dist=M.D.get(M.N[id].id).get(cust.get(i).id);
		last=cust.get(i).id;
	}
	if(customer_feasible.size()==k)break;
	
}

return customer_feasible;
	
} */

public void compute_start_outgoing_degree() throws UnknownObjectException, IloException {
	for(int i=0;i<M.N.length;i++) {
		ArrayList<customer>c=compute_probability_outgoing(i);
		prob_outgoing_aus.put(M.N[i].id, c);
		prob_outgoing.put(M.N[i].id, c);
		/*for(customer c1:c) {
			pr.println(c1.id+"\t"+c1.prob+"\t"+c1.dist);
		}*/

	}
	increasing_degree_outgoing();
}
public void probabilities_outgoing(String cu) throws UnknownObjectException, IloException {

	//pr.println("customer "+cu+"\t"+degree_outgoing.get(cu)+":"+prob_outgoing_aus.get(cu));

	if(degree_outgoing.get(cu)==0) {
	ArrayList<String> cust=new ArrayList<String>();
	closest_outgoing.put(cu, cust);
	sortedOutgoingDegree.remove(cu);
	vector_out.remove(cu);
	//update_outgoing_degree(cu);
	return;
	
	}
	if(degree_outgoing.get(cu)>0&&degree_outgoing.get(cu)<=k_prime/2) {
		ArrayList<String> cust=new ArrayList<String>();

		for(customer cus:prob_outgoing_aus.get(cu)) {
			cust.add(cus.id);

			if(!closest_ingoing.containsKey(cus.id)) {
				ArrayList<String> cust2=new ArrayList<String>();
				cust2.add(cu);
				closest_ingoing.put(cus.id, cust2);
			}
			else {
			closest_ingoing.get(cus.id).add(cu);
			}
		
			
		}
		closest_outgoing.put(cu, cust);
	//pr.println("selected outgoing arcs for customer "+cu+":"+cust);
		update_outgoing_degree(cu);
	}
	else {
		ArrayList<customer> cust=new ArrayList<customer>();

		for(customer cus:prob_outgoing_aus.get(cu)) {
			cust.add(cus);
		}
	prob_outgoing.put(cu, cust);
	vector_out.remove(cu);
	}

	
	
	
}
public void probabilities_outgoing() throws UnknownObjectException, IloException {
	
	ArrayList<String> vector_cu=new ArrayList<String> ();
	for(String st:sortedOutgoingDegree.keySet()) {
		vector_cu.add(st);
	}
	int i=0;
	String cu=vector_cu.get(i);
	while(true) {
		//pr.println("customer "+cu+"\t"+degree_outgoing.get(cu)+":"+prob_outgoing_aus.get(cu));

		if(degree_outgoing.get(cu)==0) {
		ArrayList<String> cust=new ArrayList<String>();
		closest_outgoing.put(cu, cust);
		sortedOutgoingDegree.remove(cu);
		update_outgoing_degree(cu);
		if(sortedOutgoingDegree.size()==0)break;
		i++;
		if(i>=vector_cu.size())break;

		cu=vector_cu.get(i);
		}
		if(degree_outgoing.get(cu)>0&&degree_outgoing.get(cu)<=k_prime/2) {
			ArrayList<String> cust=new ArrayList<String>();

			for(customer cus:prob_outgoing_aus.get(cu)) {
				cust.add(cus.id);

				if(!closest_ingoing.containsKey(cus.id)) {
					ArrayList<String> cust2=new ArrayList<String>();
					cust2.add(cu);
					closest_ingoing.put(cus.id, cust2);
				}
				else {
				closest_ingoing.get(cus.id).add(cu);
				}
			
				
			}
			closest_outgoing.put(cu, cust);
			//pr.println("selected outgoing arcs for customer "+cu+":"+cust);
			update_outgoing_degree(cu);
			if(sortedOutgoingDegree.size()==0)break;
			vector_cu=new ArrayList<String> ();
			for(String st:sortedOutgoingDegree.keySet()) {
				vector_cu.add(st);
			}
			i=0;
			cu=vector_cu.get(i);
		}
		else {
			ArrayList<customer> cust=new ArrayList<customer>();

			for(customer cus:prob_outgoing_aus.get(cu)) {
				cust.add(cus);
			}
		prob_outgoing.put(cu, cust);
		i++;
		if(i>=vector_cu.size())break;

		cu=vector_cu.get(i);
		}

		}
	
	}
public void feasible_arcs() {
for(int j=0;j<M.N0.length;j++) {
	for(int i=0;i<M.N0.length;i++) {

		if(M.N0[i].id.startsWith("D")||M.N0[j].id.startsWith("D"))continue;

		if(M.N0[i].id.equals(M.N0[j].id))continue;
		if(!M.feasible_demands(M.N0[i], M.N0[j]))
			{System.err.println("no feasible due to demand:"+M.N0[i]+","+M.N0[j]);
			continue;
			}
		if(!M.feasible_return_depot(M.N0[i], M.N0[j]))
		{System.err.println("no feasible due to return to depot:"+M.N0[i]+","+M.N0[j]);
		continue;
		
		}
		if(!M.feasible_time_windows(M.N0[i], M.N0[j]))
		{System.err.println("no feasible due to time window:"+M.N0[i]+","+M.N0[j]);
		continue;
		
		}
		if(arcs_out.containsKey(M.N0[i].id)) {
			arcs_out.get(M.N0[i].id).add(M.N0[j].id);
		}
		else
		{
			ArrayList<String> aus=new ArrayList<String>();
			aus.add(M.N0[j].id);
			arcs_out.put(M.N0[i].id, aus);
		}
		if(arcs_in.containsKey(M.N0[j].id)) {
			arcs_in.get(M.N0[j].id).add(M.N0[i].id);
		}
		else
		{
			ArrayList<String> aus=new ArrayList<String>();
			aus.add(M.N0[i].id);
			arcs_in.put(M.N0[j].id, aus);
		}
		
		if(degree_in.containsKey(M.N0[j].id)) {
			int count=degree_in.get(M.N0[j].id);
			degree_in.put(M.N0[j].id, count+1);
		}
		
		else {
			degree_in.put(M.N0[j].id, 1);
		}
		if(degree_out.containsKey(M.N0[i].id)) {
			int count=degree_out.get(M.N0[i].id);
			degree_out.put(M.N0[i].id, count+1);
		}
		else {
			degree_out.put(M.N0[i].id, 1);
		}
		System.err.println("feasible:"+M.N0[i]+","+M.N0[j]);
		
	}
	
}
	

}
public void compute_start_ingoing_degree() throws UnknownObjectException, IloException {
	for(int i=0;i<M.N.length;i++) {
		ArrayList<customer>c=compute_probability_ingoing(i);
		prob_ingoing_aus.put(M.N[i].id, c);
		prob_ingoing.put(M.N[i].id, c);
		/*for(customer c1:c) {
			pr.println(c1.id+"\t"+c1.prob+"\t"+c1.dist);
		}*/
	}
	increasing_degree_ingoing();
}
public void probabilities_ingoing(String cu) throws UnknownObjectException, IloException {


	//pr.println("customer "+cu+"\t"+degree_ingoing.get(cu));
	if(degree_ingoing.get(cu)==0) {
	ArrayList<String> cust=new ArrayList<String>();
	closest_ingoing.put(cu, cust);
	sortedIngoingDegree.remove(cu);
	degree_ingoing.remove(cu);
	vector_in.remove(cu);
	return;
	}
	if(degree_ingoing.get(cu)>0&&degree_ingoing.get(cu)<=k_prime/2) {
		ArrayList<String> cust=new ArrayList<String>();
		for(customer cus:prob_ingoing_aus.get(cu)) {
			cust.add(cus.id);
			if(!closest_outgoing.containsKey(cus.id)) {
				ArrayList<String> cust2=new ArrayList<String>();
				cust2.add(cu);
				closest_outgoing.put(cus.id, cust2);

			}
			else {
			closest_outgoing.get(cus.id).add(cu);
			}
		
			
		}
		closest_ingoing.put(cu, cust);
		//pr.println("selected ingoing arcs for customer "+cu+":"+cust);
		update_ingoing_degree(cu);
	}
	else {
		ArrayList<customer> cust=new ArrayList<customer>();
		for(customer cus:prob_ingoing_aus.get(cu)) {
			cust.add(cus);
		}
	prob_ingoing.put(cu, cust);
	vector_in.remove(cu);
	}
	
	
	
}
public void probabilities() throws UnknownObjectException, IloException {

	//processo i nodi in maniera ordinata per grado entrante/uscente crescente
	
	for(String st:sortedIngoingDegree.keySet()) {
		vector_in.add(st);
	}
	
	for(String st:sortedOutgoingDegree.keySet()) {
		vector_out.add(st);
	}
	int i=0;
	int o=0;
	while(true) {
		if(vector_in.size()==0&&vector_out.size()==0)break;
		String cu=null;
		
		if((i<vector_in.size()&&vector_out.size()==0)||!degree_outgoing.containsKey(vector_out.get(o))) {
			
				cu=vector_in.get(i);
				//pr.println("customer "+cu+"\t ingoing degree "+degree_ingoing.get(cu));
				probabilities_ingoing(cu);
				
			
		}
		else {
			if((vector_out.size()!=0&&vector_in.size()==0)||!degree_ingoing.containsKey(vector_in.get(i))) {
				
					cu=vector_out.get(o);
				//pr.println("customer "+cu+"\t outgoing degree "+degree_outgoing.get(cu));
					probabilities_outgoing(cu);
								
			}
			else {
				if(sortedIngoingDegree.get(vector_in.get(i))<sortedOutgoingDegree.get(vector_out.get(o))) {
					cu=vector_in.get(i);
					//pr.println("customer "+cu+"\t ingoing degree "+degree_ingoing.get(cu));
					probabilities_ingoing(cu);
					
					}
				else {

					cu=vector_out.get(o);
				//pr.println("customer "+cu+"\t outgoing degree "+degree_outgoing.get(cu));
					probabilities_outgoing(cu);
						
					
				}
			}
		}
		
		
		}
	
	
}
public void probabilities_ingoing() throws UnknownObjectException, IloException {
	//processo i nodi in maniera ordinata per grado entrante crescente
	ArrayList<String> vector_cu=new ArrayList<String> ();
	for(String st:sortedIngoingDegree.keySet()) {
		vector_cu.add(st);
	}
	int i=0;
	String cu=vector_cu.get(i);
	while(true) {
		//pr.println("customer "+cu+"\t"+degree_ingoing.get(cu));
		if(degree_ingoing.get(cu)==0) {
		ArrayList<String> cust=new ArrayList<String>();
		closest_ingoing.put(cu, cust);
		sortedIngoingDegree.remove(cu);
		degree_ingoing.remove(cu);
		if(sortedIngoingDegree.size()==0)break;
		i++;
		if(i>=vector_cu.size())break;
		cu=vector_cu.get(i);
		}
		if(degree_ingoing.get(cu)>0&&degree_ingoing.get(cu)<=k_prime/2) {
			ArrayList<String> cust=new ArrayList<String>();
			for(customer cus:prob_ingoing_aus.get(cu)) {
				cust.add(cus.id);
				if(!closest_outgoing.containsKey(cus.id)) {
					ArrayList<String> cust2=new ArrayList<String>();
					cust2.add(cu);
					closest_outgoing.put(cus.id, cust2);

				}
				else {
				closest_outgoing.get(cus.id).add(cu);
				}
			
				
			}
			closest_ingoing.put(cu, cust);
			//pr.println("selected ingoing arcs for customer "+cu+":"+cust);
			update_ingoing_degree(cu);
			if(sortedIngoingDegree.size()==0)break;
			vector_cu=new ArrayList<String> ();
			for(String st:sortedIngoingDegree.keySet()) {
				vector_cu.add(st);
			}
			i=0;
			cu=vector_cu.get(i);
		}
		else {
			ArrayList<customer> cust=new ArrayList<customer>();
			for(customer cus:prob_ingoing_aus.get(cu)) {
				cust.add(cus);
			}
		prob_ingoing.put(cu, cust);
		i++;
		if(i>=vector_cu.size())break;
		cu=vector_cu.get(i);
		}
		}
	
	}

public void update_ingoing_degree(String c) throws UnknownObjectException, IloException {
	int i=0;
	//System.err.println("customer "+c+"\t"+closest_ingoing.get(c).size());
	if(prob_ingoing_aus.get(c).size()==0) {
		degree_ingoing.remove(c);
		vector_in.remove(c);
		increasing_degree_ingoing();
		increasing_degree_outgoing();
		return;
	}
	
	while(true) {
		String cu=closest_ingoing.get(c).get(i);
		if(degree_outgoing.containsKey(cu)) {
		int size1=degree_outgoing.get(cu);
		if(size1>0&&size1-1>0)
		degree_outgoing.put(cu, size1-1);	
		else {
		degree_outgoing.remove(cu);
		vector_out.remove(cu);
		}
		}
		if(closest_outgoing.containsKey(cu)&&closest_outgoing.get(cu).size()==k_prime/2){
		degree_outgoing.remove(cu);
		vector_out.remove(cu);
		for(customer cu2:prob_outgoing_aus.get(cu)) {
			boolean exist=false;
			ArrayList<customer> prob_ingoing_aus_1=new ArrayList<customer>();
			for(customer c8:prob_ingoing_aus.get(cu2.id))
				if(!c8.id.equals(cu))
					prob_ingoing_aus_1.add(c8);
				else
					exist=true;
			prob_ingoing_aus.put(cu2.id, prob_ingoing_aus_1);
			/*if(exist) {
			pr.println("il grado uscente di "+cu+":"+(closest_outgoing.get(cu).size())+" quindi rimuovo questo nodo dalla stella entrante del nodo: [");
			pr.println(cu2.id+":"+prob_ingoing_aus.get(cu2.id));
			pr.println("]");
			}*/

			if(degree_ingoing.containsKey(cu2.id)&&exist) {
			int size1=degree_ingoing.get(cu2.id);
			degree_ingoing.put(cu2.id, size1-1);
			}
		}
		
	}
		i++;
		if(i>=closest_ingoing.get(c).size())break;
		cu=closest_ingoing.get(c).get(i);
		}
	degree_ingoing.remove(c);
	vector_in.remove(c);
	increasing_degree_ingoing();
	increasing_degree_outgoing();
}
public void update_outgoing_degree(String c) throws UnknownObjectException, IloException {
	int i=0;
	//System.err.println("customer "+c+"\t"+closest_outgoing.get(c).size());
	if(prob_outgoing_aus.get(c).size()==0) {
		degree_outgoing.remove(c);
		vector_out.remove(c);
		increasing_degree_ingoing();
		increasing_degree_outgoing();
		return;
	}
	while(true) {
		String cu=closest_outgoing.get(c).get(i);
		if(degree_ingoing.containsKey(cu)) {
		int size1=degree_ingoing.get(cu);
		if(size1>0&&size1-1>0)
		degree_ingoing.put(cu, size1-1);
		else {
		degree_ingoing.remove(cu);
		vector_in.remove(cu);
		}
		}
		if(closest_ingoing.containsKey(cu)&&closest_ingoing.get(cu).size()==k_prime/2){
		degree_ingoing.remove(cu);
		vector_in.remove(cu);
		for(customer c2:prob_ingoing_aus.get(cu)) {
			boolean exist=false;
			ArrayList<customer> prob_outgoing_aus_1=new ArrayList<customer>();
			for(customer c8:prob_outgoing_aus.get(c2.id))
				if(!c8.id.equals(cu))
					prob_outgoing_aus_1.add(c8);
				else
					exist=true;
			prob_outgoing_aus.put(c2.id, prob_outgoing_aus_1);
		/*	if(exist) {
			pr.println("il grado entrante di "+cu+":"+(closest_ingoing.get(cu).size())+" quindi rimuovo questo nodo dalla stella uscente del nodo: [");
			pr.println(c2.id+":"+prob_outgoing_aus.get(c2.id));
			pr.println("]");
			}*/
			if(degree_outgoing.containsKey(c2.id)&&exist) {
			int size1=degree_outgoing.get(c2.id);
			degree_outgoing.put(c2.id, size1-1);
			}
		}
		
		
		}
		i++;
		if(i>=closest_outgoing.get(c).size())break;
		cu=closest_outgoing.get(c).get(i);
		}
	degree_outgoing.remove(c);
	vector_out.remove(c);
	increasing_degree_ingoing();
	increasing_degree_outgoing();
}
public void random_extract() throws UnknownObjectException, IloException {

	vector_in.clear();
	for(String st:sortedIngoingDegree.keySet()) {
		vector_in.add(st);
	}
	vector_out.clear();
	for(String st:sortedOutgoingDegree.keySet()) {
		vector_out.add(st);
	}
	int i=0;
	int o=0;
	while(true) {
		if(vector_in.size()==0&&vector_out.size()==0)break;
		String cu=null;
		if((vector_in.size()!=0&&vector_out.size()==0)||!degree_outgoing.containsKey(vector_out.get(o))) {
			
				cu=vector_in.get(i);			
				
				//pr.println("customer "+cu+"\t ingoing degree "+degree_ingoing.get(cu));
				if(degree_ingoing.get(cu)>=k_prime/2)
				random_extract_ingoing(cu);
				else
				{//pr.println("invoco la procedura plus");
					random_extract_ingoing_plus(cu);
				}
				
			
		}
		else {
			if((vector_out.size()!=0&&vector_in.size()==0)||!degree_ingoing.containsKey(vector_in.get(i))) {
				
					cu=vector_out.get(o);
					//pr.println("customer "+cu+"\t outgoing degree "+degree_outgoing.get(cu));
					if(degree_outgoing.get(cu)>=k_prime/2)
						random_extract_outgoing(cu);
						else
						{//pr.println("invoco la procedura plus");
							random_extract_outgoing_plus(cu);
						}
					
				
			}
			else {
				//System.out.println("vector in:"+vector_in.size()+"\t"+"vector out:"+vector_out.size()+"\t"+sortedIngoingDegree.get(vector_in.get(i))+"\t"+sortedOutgoingDegree.get(vector_out.get(o)));

				if(sortedIngoingDegree.get(vector_in.get(i))<sortedOutgoingDegree.get(vector_out.get(o))) {
					cu=vector_in.get(i);
					//pr.println("customer "+cu+"\t ingoing degree "+degree_ingoing.get(cu)+"\t k prime"+k_prime);
					if(degree_ingoing.get(cu)>=k_prime/2)
						random_extract_ingoing(cu);
						else {
							//pr.println("invoco la procedura plus");
							random_extract_ingoing_plus(cu);
						}
					
					}
				else {
					cu=vector_out.get(o);
					//System.out.println("customer "+cu+"\t outgoing degree "+degree_outgoing.get(cu)+"\t k prime"+k_prime);

					//pr.println("customer "+cu+"\t outgoing degree "+degree_outgoing.get(cu));
					if(degree_outgoing.get(cu)>=k_prime/2)
						random_extract_outgoing(cu);
						else {
							//pr.println("invoco la procedura plus");
							random_extract_outgoing_plus(cu);
						}
						
					
				}
			}
		}
		
		
		}
	
	


}
public void random_extract_ingoing_plus(String cu) throws UnknownObjectException, IloException {


	//pr.println("extract ingoing arcs for customer "+cu+" with ingoing degree "+degree_ingoing.get(cu));
	//if(closest_ingoing.containsKey(cu))pr.println(closest_ingoing.get(cu));
	int count=0;
	if(closest_ingoing.containsKey(cu))
		count=closest_ingoing.get(cu).size();
	/*for(customer c1:prob_ingoing_aus.get(cu)) {
		pr.print("outgoing customers for "+c1.id+":");
		if(closest_outgoing.containsKey(c1.id)) {
			for(String st:closest_outgoing.get(c1.id)) {
				pr.print(st+"\t");
			}
			
		}
		pr.println();
	}*/
	//System.out.println("devo trovare altri:"+(k_prime/2-count)+" archi");
	ArrayList<String> cust=new ArrayList<String>();
	if(k_prime/2-count<=k_prime/2) {
		for(int j=0;j<prob_ingoing.get(cu).size();j++) {

			if(closest_ingoing.containsKey(cu)) {
				if(!closest_ingoing.get(cu).contains(prob_ingoing.get(cu).get(j).id)&&count<k_prime/2) {
			if(closest_outgoing.keySet().contains(prob_ingoing.get(cu).get(j).id)
					) {

				closest_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
				closest_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
				//pr.println("selected :"+prob_ingoing.get(cu).get(j).id);

				if(random_ingoing.containsKey(cu))
					random_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
				else {
					ArrayList<String> aus=new ArrayList<String>();
					aus.add(prob_ingoing.get(cu).get(j).id);
					random_ingoing.put(cu, aus);
				}
				if(random_outgoing.containsKey(prob_ingoing.get(cu).get(j).id))
					random_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
				else {
					ArrayList<String> aus=new ArrayList<String>();
					aus.add(cu);
					random_outgoing.put(prob_ingoing.get(cu).get(j).id, aus);
				}
				count++;
			
			}
			else {
				if(!closest_outgoing.keySet().contains(prob_ingoing.get(cu).get(j).id)) {

					closest_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
					ArrayList<String> cust2=new ArrayList<String>();
					cust2.add(cu);
					closest_outgoing.put(prob_ingoing.get(cu).get(j).id, cust2);
					//pr.println("selected :"+prob_ingoing.get(cu).get(j).id);
					if(random_ingoing.containsKey(cu))
						random_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
					else {
						ArrayList<String> aus=new ArrayList<String>();
						aus.add(prob_ingoing.get(cu).get(j).id);
						random_ingoing.put(cu, aus);
					}
					if(random_outgoing.containsKey(prob_ingoing.get(cu).get(j).id))
						random_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
					else {
						ArrayList<String> aus=new ArrayList<String>();
						aus.add(cu);
						random_outgoing.put(prob_ingoing.get(cu).get(j).id, aus);
					}
					count++;
					
				}
			}
				}
				
			}
			else {
				if(count<k_prime/2) {
				if(closest_outgoing.keySet().contains(prob_ingoing.get(cu).get(j).id)
						) {

					cust.add(prob_ingoing.get(cu).get(j).id);
					closest_ingoing.put(cu, cust);
					closest_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
					//pr.println("selected :"+prob_ingoing.get(cu).get(j).id);
					if(random_ingoing.containsKey(cu))
						random_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
					else {
						ArrayList<String> aus=new ArrayList<String>();
						aus.add(prob_ingoing.get(cu).get(j).id);
						random_ingoing.put(cu, aus);
					}
					if(random_outgoing.containsKey(prob_ingoing.get(cu).get(j).id))
						random_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
					else {
						ArrayList<String> aus=new ArrayList<String>();
						aus.add(cu);
						random_outgoing.put(prob_ingoing.get(cu).get(j).id, aus);
					}
					count++;
					
					}
					else {
						if(!closest_outgoing.keySet().contains(prob_ingoing.get(cu).get(j).id)) {

							cust.add(prob_ingoing.get(cu).get(j).id);
							closest_ingoing.put(cu, cust);
							ArrayList<String> cust2=new ArrayList<String>();
							cust2.add(cu);
							closest_outgoing.put(prob_ingoing.get(cu).get(j).id, cust2);
							//pr.println("selected :"+prob_ingoing.get(cu).get(j).id);
							if(random_ingoing.containsKey(cu))
								random_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(prob_ingoing.get(cu).get(j).id);
								random_ingoing.put(cu, aus);
							}
							if(random_outgoing.containsKey(prob_ingoing.get(cu).get(j).id))
								random_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(cu);
								random_outgoing.put(prob_ingoing.get(cu).get(j).id, aus);
							}
							count++;
							
							
						}
				}
			}
		}
		
		}
	}
	else {
		
	while(count<k_prime/2) {
		double v=gen_ingoing.nextDouble();
		//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
		while(v==1) {
			v=gen_ingoing.nextDouble();
			//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
			}
		//pr.println("selected v:"+v);
		double sum=0;
			for(int j=0;j<prob_ingoing.get(cu).size();j++) {
				if(v>=sum&&v<sum+prob_ingoing.get(cu).get(j).prob) {
					if(closest_ingoing.containsKey(cu)) {
						if(!closest_ingoing.get(cu).contains(prob_ingoing.get(cu).get(j).id)&&count<k_prime/2) {
					if(closest_outgoing.keySet().contains(prob_ingoing.get(cu).get(j).id)
							) {

						closest_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
						closest_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
						//pr.println("selected :"+prob_ingoing.get(cu).get(j).id);

						if(random_ingoing.containsKey(cu))
							random_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(prob_ingoing.get(cu).get(j).id);
							random_ingoing.put(cu, aus);
						}
						if(random_outgoing.containsKey(prob_ingoing.get(cu).get(j).id))
							random_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(cu);
							random_outgoing.put(prob_ingoing.get(cu).get(j).id, aus);
						}
						count++;
					
					}
					else {
						if(!closest_outgoing.keySet().contains(prob_ingoing.get(cu).get(j).id)) {

							closest_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
							ArrayList<String> cust2=new ArrayList<String>();
							cust2.add(cu);
							closest_outgoing.put(prob_ingoing.get(cu).get(j).id, cust2);
							//pr.println("selected :"+prob_ingoing.get(cu).get(j).id);
							if(random_ingoing.containsKey(cu))
								random_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(prob_ingoing.get(cu).get(j).id);
								random_ingoing.put(cu, aus);
							}
							if(random_outgoing.containsKey(prob_ingoing.get(cu).get(j).id))
								random_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(cu);
								random_outgoing.put(prob_ingoing.get(cu).get(j).id, aus);
							}
							count++;
							
						}
					}
						}
						
					}
					else {
						if(count<k_prime/2) {
						if(closest_outgoing.keySet().contains(prob_ingoing.get(cu).get(j).id)
								) {

							cust.add(prob_ingoing.get(cu).get(j).id);
							closest_ingoing.put(cu, cust);
							closest_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
							//pr.println("selected :"+prob_ingoing.get(cu).get(j).id);
							if(random_ingoing.containsKey(cu))
								random_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(prob_ingoing.get(cu).get(j).id);
								random_ingoing.put(cu, aus);
							}
							if(random_outgoing.containsKey(prob_ingoing.get(cu).get(j).id))
								random_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(cu);
								random_outgoing.put(prob_ingoing.get(cu).get(j).id, aus);
							}
							count++;
							
							}
							else {
								if(!closest_outgoing.keySet().contains(prob_ingoing.get(cu).get(j).id)) {

									cust.add(prob_ingoing.get(cu).get(j).id);
									closest_ingoing.put(cu, cust);
									ArrayList<String> cust2=new ArrayList<String>();
									cust2.add(cu);
									closest_outgoing.put(prob_ingoing.get(cu).get(j).id, cust2);
									//pr.println("selected :"+prob_ingoing.get(cu).get(j).id);
									if(random_ingoing.containsKey(cu))
										random_ingoing.get(cu).add(prob_ingoing.get(cu).get(j).id);
									else {
										ArrayList<String> aus=new ArrayList<String>();
										aus.add(prob_ingoing.get(cu).get(j).id);
										random_ingoing.put(cu, aus);
									}
									if(random_outgoing.containsKey(prob_ingoing.get(cu).get(j).id))
										random_outgoing.get(prob_ingoing.get(cu).get(j).id).add(cu);
									else {
										ArrayList<String> aus=new ArrayList<String>();
										aus.add(cu);
										random_outgoing.put(prob_ingoing.get(cu).get(j).id, aus);
									}
									count++;
									
									
								}
						}
					}
				}
				}
				else
				{	

					sum+=prob_ingoing.get(cu).get(j).prob;
					//pr.println("probabilita di "+prob_ingoing_aus.get(cu).get(j).id+":"+prob_ingoing_aus.get(cu).get(j).prob);
				}
				if(count==k_prime/2)break;
			}
			

	}
}
	//pr.println("ingoing customers compatible for:"+cu+"\t"+"selected:"+closest_ingoing.get(cu));
	sortedIngoingDegree.remove(cu);
	update_ingoing_degree(cu);

	



}
public void random_extract_ingoing(String cu) throws UnknownObjectException, IloException {

	//pr.println("extract ingoing arcs for customer "+cu+" with ingoing degree "+degree_ingoing.get(cu));
	//if(closest_ingoing.containsKey(cu))
		//System.out.println(closest_ingoing.get(cu));
	if(closest_ingoing.containsKey(cu)&&closest_ingoing.get(cu).size()==k_prime/2)
		{sortedIngoingDegree.remove(cu);
		update_ingoing_degree(cu);
		return;
		
		}
	int count=0;
	if(closest_ingoing.containsKey(cu))
	count=closest_ingoing.get(cu).size();
	if(count>=k_prime/2)
	{sortedIngoingDegree.remove(cu);
	update_ingoing_degree(cu);
	return;
	
	}
	boolean esci=false;
	int count1=0;
	//pr.println("prob:"+prob_ingoing_aus.size());
	for(customer c1:prob_ingoing_aus.get(cu)) {
		//pr.println("outgoing customers for "+c1.id+":");
		if(closest_outgoing.containsKey(c1.id)&&closest_outgoing.get(c1.id).size()<k_prime/2) {
			if(closest_ingoing.containsKey(cu)&&!closest_ingoing.get(cu).contains(c1.id))
				count1++;
				else
					if(!closest_ingoing.containsKey(cu))
			count1++;
			if(count1==(k_prime/2-count)) {
				esci=true;
			}
		}
		/*if(closest_outgoing.containsKey(c1.id)) {
			for(String st:closest_outgoing.get(c1.id)) {
				pr.print(st+"\t");
			}
			
		}
		pr.println();*/
	}
	if(!esci||(k_prime/2-count)==1) {
		random_extract_ingoing_plus(cu);
		return;
	}
	ArrayList<String> cust=new ArrayList<String>();
	while(count<k_prime/2) {
		double v=gen_ingoing.nextDouble();
		//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
		while(v==1) {
			v=gen_ingoing.nextDouble();
			//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
			}
		double sum=0;
		//System.out.println("selected v:"+v);
			for(int j=0;j<prob_ingoing_aus.get(cu).size();j++) {
				if(v>=sum&&v<sum+prob_ingoing_aus.get(cu).get(j).prob) {
					if(closest_ingoing.containsKey(cu)) {
						if(!closest_ingoing.get(cu).contains(prob_ingoing_aus.get(cu).get(j).id)&&count<k_prime/2) {
					if(closest_outgoing.keySet().contains(prob_ingoing_aus.get(cu).get(j).id)
							&&closest_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).size()<k_prime/2) {
						//System.out.println("selected :"+prob_ingoing_aus.get(cu).get(j).id);

						closest_ingoing.get(cu).add(prob_ingoing_aus.get(cu).get(j).id);
						closest_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).add(cu);
						if(random_ingoing.containsKey(cu))
							random_ingoing.get(cu).add(prob_ingoing_aus.get(cu).get(j).id);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(prob_ingoing_aus.get(cu).get(j).id);
							random_ingoing.put(cu, aus);
						}
						if(random_outgoing.containsKey(prob_ingoing_aus.get(cu).get(j).id))
							random_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).add(cu);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(cu);
							random_outgoing.put(prob_ingoing_aus.get(cu).get(j).id, aus);
						}
						count++;
					
					}
					else {
						if(!closest_outgoing.keySet().contains(prob_ingoing_aus.get(cu).get(j).id)) {
							closest_ingoing.get(cu).add(prob_ingoing_aus.get(cu).get(j).id);
							ArrayList<String> cust2=new ArrayList<String>();
							cust2.add(cu);
							closest_outgoing.put(prob_ingoing_aus.get(cu).get(j).id, cust2);
							//System.out.println("selected :"+prob_ingoing_aus.get(cu).get(j).id);

							if(random_ingoing.containsKey(cu))
								random_ingoing.get(cu).add(prob_ingoing_aus.get(cu).get(j).id);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(prob_ingoing_aus.get(cu).get(j).id);
								random_ingoing.put(cu, aus);
							}
							if(random_outgoing.containsKey(prob_ingoing_aus.get(cu).get(j).id))
								random_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).add(cu);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(cu);
								random_outgoing.put(prob_ingoing_aus.get(cu).get(j).id, aus);
							}
							count++;
							
						}
					}
						}
						
					}
					else {
						if(count<k_prime/2) {
						if(closest_outgoing.keySet().contains(prob_ingoing_aus.get(cu).get(j).id)&&closest_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).size()<k_prime/2) {
							cust.add(prob_ingoing_aus.get(cu).get(j).id);
							closest_ingoing.put(cu, cust);
							closest_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).add(cu);
							//System.out.println("selected :"+prob_ingoing_aus.get(cu).get(j).id);

							if(random_ingoing.containsKey(cu))
								random_ingoing.get(cu).add(prob_ingoing_aus.get(cu).get(j).id);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(prob_ingoing_aus.get(cu).get(j).id);
								random_ingoing.put(cu, aus);
							}
							if(random_outgoing.containsKey(prob_ingoing_aus.get(cu).get(j).id))
								random_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).add(cu);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(cu);
								random_outgoing.put(prob_ingoing_aus.get(cu).get(j).id, aus);
							}
							count++;
							
							}
							else {
								if(!closest_outgoing.keySet().contains(prob_ingoing_aus.get(cu).get(j).id)) {
									cust.add(prob_ingoing_aus.get(cu).get(j).id);
									closest_ingoing.put(cu, cust);
									ArrayList<String> cust2=new ArrayList<String>();
									cust2.add(cu);
									closest_outgoing.put(prob_ingoing_aus.get(cu).get(j).id, cust2);
									//System.out.println("selected :"+prob_ingoing_aus.get(cu).get(j).id);

									if(random_ingoing.containsKey(cu))
										random_ingoing.get(cu).add(prob_ingoing_aus.get(cu).get(j).id);
									else {
										ArrayList<String> aus=new ArrayList<String>();
										aus.add(prob_ingoing_aus.get(cu).get(j).id);
										random_ingoing.put(cu, aus);
									}
									if(random_outgoing.containsKey(prob_ingoing_aus.get(cu).get(j).id))
										random_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).add(cu);
									else {
										ArrayList<String> aus=new ArrayList<String>();
										aus.add(cu);
										random_outgoing.put(prob_ingoing_aus.get(cu).get(j).id, aus);
									}
									count++;
									
									
								}
						}
					}
				}
				}
				else
				{	

					sum+=prob_ingoing_aus.get(cu).get(j).prob;
				}
			}
			

	}
	//pr.println("ingoing customers compatible for:"+cu+"\t"+"selected:"+closest_ingoing.get(cu));
	sortedIngoingDegree.remove(cu);
	update_ingoing_degree(cu);

	


}
public void random_extract_ingoing() throws UnknownObjectException, IloException {
	ArrayList<String> vector_cu=new ArrayList<String> ();
	for(String st:sortedIngoingDegree.keySet()) {
		vector_cu.add(st);
	}
	int i=0;
	String cu=vector_cu.get(i);
	while(true) {
		//pr.println("extract ingoing arcs for customer "+cu+" with ingoing degree "+degree_ingoing.get(cu));
		//if(closest_ingoing.containsKey(cu))pr.println(closest_ingoing.get(cu));
		if(closest_ingoing.containsKey(cu)&&closest_ingoing.get(cu).size()==k_prime/2)continue;
		int count=0;
		if(closest_ingoing.containsKey(cu))
		count=closest_ingoing.get(cu).size();
		//pr.println("count:"+count);
		/*for(customer c1:prob_ingoing_aus.get(cu)) {
			pr.print("outgoing customers for "+c1.id+":");
			if(closest_outgoing.containsKey(c1.id)) {
				for(String st:closest_outgoing.get(c1.id)) {
					pr.print(st+"\t");
				}
				
			}
			pr.println();
		}*/
		ArrayList<String> cust=new ArrayList<String>();
		while(count<k_prime/2) {
			double v=gen_ingoing.nextDouble();
			//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
			while(v==1) {
				v=gen_ingoing.nextDouble();
				//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
				}
			double sum=0;
				for(int j=0;j<prob_ingoing_aus.get(cu).size();j++) {
					if(v>=sum&&v<sum+prob_ingoing_aus.get(cu).get(j).prob) {
						if(closest_ingoing.containsKey(cu)) {
							if(!closest_ingoing.get(cu).contains(prob_ingoing_aus.get(cu).get(j).id)&&count<k_prime/2) {
						if(closest_outgoing.keySet().contains(prob_ingoing_aus.get(cu).get(j).id)
								&&closest_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).size()<k_prime/2) {

							closest_ingoing.get(cu).add(prob_ingoing_aus.get(cu).get(j).id);
							closest_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).add(cu);
							count++;
							break;
						
						}
						else {
							if(!closest_outgoing.keySet().contains(prob_ingoing_aus.get(cu).get(j).id)) {

								closest_ingoing.get(cu).add(prob_ingoing_aus.get(cu).get(j).id);
								ArrayList<String> cust2=new ArrayList<String>();
								cust2.add(cu);
								closest_outgoing.put(prob_ingoing_aus.get(cu).get(j).id, cust2);
								count++;
								break;
								
							}
						}
							}
							else {
								break;
							}
						}
						else {
							if(count<k_prime/2) {
							if(closest_outgoing.keySet().contains(prob_ingoing_aus.get(cu).get(j).id)&&closest_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).size()<k_prime/2) {

								cust.add(prob_ingoing_aus.get(cu).get(j).id);
								closest_ingoing.put(cu, cust);
								closest_outgoing.get(prob_ingoing_aus.get(cu).get(j).id).add(cu);
								count++;
								break;
								
								}
								else {
									if(!closest_outgoing.keySet().contains(prob_ingoing_aus.get(cu).get(j).id)) {

										cust.add(prob_ingoing_aus.get(cu).get(j).id);
										closest_ingoing.put(cu, cust);
										ArrayList<String> cust2=new ArrayList<String>();
										cust2.add(cu);
										closest_outgoing.put(prob_ingoing_aus.get(cu).get(j).id, cust2);
										count++;
										break;
										
									}
							}
						}
					}
					}
					else
					{	

						sum+=prob_ingoing_aus.get(cu).get(j).prob;
						//pr.println("probabilita di "+prob_ingoing_aus.get(cu).get(j).id+":"+prob_ingoing_aus.get(cu).get(j).prob);
					}
				}
				

		}
		//pr.println("ingoing customers compatible for:"+cu+"\t"+"selected:"+closest_ingoing.get(cu));
		sortedIngoingDegree.remove(cu);
		update_ingoing_degree(cu);
		if(sortedIngoingDegree.size()==0)break;
		vector_cu=new ArrayList<String> ();
		for(String st:sortedIngoingDegree.keySet()) {
			vector_cu.add(st);
		}
		i=0;
		cu=vector_cu.get(i);
	}
	

}
public void random_extract_outgoing_plus(String cu) throws UnknownObjectException, IloException {


	vector_out.clear();
	for(String st:sortedOutgoingDegree.keySet()) {
		vector_out.add(st);
	}
	int count=0;
	if(closest_outgoing.containsKey(cu))
		count=closest_outgoing.get(cu).size();
	//System.out.println("devo trovare altri:"+(k_prime/2-count)+" archi");
	ArrayList<String> cust=new ArrayList<String>();
	if(k_prime/2-count<=k_prime/2) {
		for(int j=0;j<prob_outgoing.get(cu).size();j++) {


			if(closest_outgoing.containsKey(cu)) {
				if(!closest_outgoing.get(cu).contains(prob_outgoing.get(cu).get(j).id)&&count<k_prime/2) {
			if(closest_ingoing.containsKey(prob_outgoing.get(cu).get(j).id)) {

				closest_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
				closest_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
				//System.out.println("selected :"+prob_outgoing.get(cu).get(j).id);

				if(random_outgoing.containsKey(cu))
					random_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
				else {
					ArrayList<String> aus=new ArrayList<String>();
					aus.add(prob_outgoing.get(cu).get(j).id);
					random_outgoing.put(cu, aus);
				}
				if(random_ingoing.containsKey(prob_outgoing.get(cu).get(j).id))
					random_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
				else {
					ArrayList<String> aus=new ArrayList<String>();
					aus.add(cu);
					random_ingoing.put(prob_outgoing.get(cu).get(j).id, aus);
				}
				count++;
					
			}
			else {
				if(!closest_ingoing.containsKey(prob_outgoing.get(cu).get(j).id)) {

					ArrayList<String> cust2=new ArrayList<String>();
					cust2.add(cu);
					closest_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
					closest_ingoing.put(prob_outgoing.get(cu).get(j).id, cust2);
					//System.out.println("selected :"+prob_outgoing.get(cu).get(j).id);

					if(random_outgoing.containsKey(cu))
						random_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
					else {
						ArrayList<String> aus=new ArrayList<String>();
						aus.add(prob_outgoing.get(cu).get(j).id);
						random_outgoing.put(cu, aus);
					}
					if(random_ingoing.containsKey(prob_outgoing.get(cu).get(j).id))
						random_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
					else {
						ArrayList<String> aus=new ArrayList<String>();
						aus.add(cu);
						random_ingoing.put(prob_outgoing.get(cu).get(j).id, aus);
					}
					count++;
					
				}
			}
				}
				
			}
			else {
				if(count<k_prime/2) {
				
				if(closest_ingoing.containsKey(prob_outgoing.get(cu).get(j).id)) {

					cust.add(prob_outgoing.get(cu).get(j).id);
					closest_outgoing.put(cu, cust);
				closest_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
				//System.out.println("selected :"+prob_outgoing.get(cu).get(j).id);

				if(random_outgoing.containsKey(cu))
					random_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
				else {
					ArrayList<String> aus=new ArrayList<String>();
					aus.add(prob_outgoing.get(cu).get(j).id);
					random_outgoing.put(cu, aus);
				}
				if(random_ingoing.containsKey(prob_outgoing.get(cu).get(j).id))
					random_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
				else {
					ArrayList<String> aus=new ArrayList<String>();
					aus.add(cu);
					random_ingoing.put(prob_outgoing.get(cu).get(j).id, aus);
				}
				count++;
					
				}
				else {
					if(!closest_ingoing.containsKey(prob_outgoing.get(cu).get(j).id)) {

						ArrayList<String> cust2=new ArrayList<String>();
						cust2.add(cu);
						cust.add(prob_outgoing.get(cu).get(j).id);
						closest_outgoing.put(cu, cust);
						closest_ingoing.put(prob_outgoing.get(cu).get(j).id, cust2);
						//System.out.println("selected :"+prob_outgoing.get(cu).get(j).id);

						if(random_outgoing.containsKey(cu))
							random_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(prob_outgoing.get(cu).get(j).id);
							random_outgoing.put(cu, aus);
						}
						if(random_ingoing.containsKey(prob_outgoing.get(cu).get(j).id))
							random_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(cu);
							random_ingoing.put(prob_outgoing.get(cu).get(j).id, aus);
						}
						count++;
						
					}
				}
			}
		}
		
		
		}
	}
	else {
	while(count<k_prime/2) {
		double v=gen_ingoing.nextDouble();
		//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
		while(v==1) {
			v=gen_ingoing.nextDouble();
			//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
			}
		//pr.println("selected v:"+v);

		double sum=0;
			for(int j=0;j<prob_outgoing.get(cu).size();j++) {
				if(v>=sum&&v<sum+prob_outgoing.get(cu).get(j).prob) {
					if(closest_outgoing.containsKey(cu)) {
						if(!closest_outgoing.get(cu).contains(prob_outgoing.get(cu).get(j).id)&&count<k_prime/2) {
					if(closest_ingoing.containsKey(prob_outgoing.get(cu).get(j).id)) {

						closest_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
						closest_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
						//System.out.println("selected :"+prob_outgoing.get(cu).get(j).id);

						if(random_outgoing.containsKey(cu))
							random_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(prob_outgoing.get(cu).get(j).id);
							random_outgoing.put(cu, aus);
						}
						if(random_ingoing.containsKey(prob_outgoing.get(cu).get(j).id))
							random_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(cu);
							random_ingoing.put(prob_outgoing.get(cu).get(j).id, aus);
						}
						count++;
							
					}
					else {
						if(!closest_ingoing.containsKey(prob_outgoing.get(cu).get(j).id)) {

							ArrayList<String> cust2=new ArrayList<String>();
							cust2.add(cu);
							closest_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
							closest_ingoing.put(prob_outgoing.get(cu).get(j).id, cust2);
							//System.out.println("selected :"+prob_outgoing.get(cu).get(j).id);

							if(random_outgoing.containsKey(cu))
								random_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(prob_outgoing.get(cu).get(j).id);
								random_outgoing.put(cu, aus);
							}
							if(random_ingoing.containsKey(prob_outgoing.get(cu).get(j).id))
								random_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(cu);
								random_ingoing.put(prob_outgoing.get(cu).get(j).id, aus);
							}
							count++;
							
						}
					}
						}
						
					}
					else {
						if(count<k_prime/2) {
						
						if(closest_ingoing.containsKey(prob_outgoing.get(cu).get(j).id)) {

							cust.add(prob_outgoing.get(cu).get(j).id);
							closest_outgoing.put(cu, cust);
						closest_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
						//System.out.println("selected :"+prob_outgoing.get(cu).get(j).id);

						if(random_outgoing.containsKey(cu))
							random_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(prob_outgoing.get(cu).get(j).id);
							random_outgoing.put(cu, aus);
						}
						if(random_ingoing.containsKey(prob_outgoing.get(cu).get(j).id))
							random_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(cu);
							random_ingoing.put(prob_outgoing.get(cu).get(j).id, aus);
						}
						count++;
							
						}
						else {
							if(!closest_ingoing.containsKey(prob_outgoing.get(cu).get(j).id)) {

								ArrayList<String> cust2=new ArrayList<String>();
								cust2.add(cu);
								cust.add(prob_outgoing.get(cu).get(j).id);
								closest_outgoing.put(cu, cust);
								closest_ingoing.put(prob_outgoing.get(cu).get(j).id, cust2);
								//System.out.println("selected :"+prob_outgoing.get(cu).get(j).id);

								if(random_outgoing.containsKey(cu))
									random_outgoing.get(cu).add(prob_outgoing.get(cu).get(j).id);
								else {
									ArrayList<String> aus=new ArrayList<String>();
									aus.add(prob_outgoing.get(cu).get(j).id);
									random_outgoing.put(cu, aus);
								}
								if(random_ingoing.containsKey(prob_outgoing.get(cu).get(j).id))
									random_ingoing.get(prob_outgoing.get(cu).get(j).id).add(cu);
								else {
									ArrayList<String> aus=new ArrayList<String>();
									aus.add(cu);
									random_ingoing.put(prob_outgoing.get(cu).get(j).id, aus);
								}
								count++;
								
							}
						}
					}
				}
				}
				else
					sum+=prob_outgoing.get(cu).get(j).prob;
				if(count==k_prime/2)break;
			}
			

	}
}
	//System.out.println("outgoing customers compatible for:"+cu+"\t"+"selected:"+closest_outgoing.get(cu));
	sortedOutgoingDegree.remove(cu);
	update_outgoing_degree(cu);
	

	



}
public void random_extract_outgoing(String cu) throws UnknownObjectException, IloException {

	vector_out.clear();
	for(String st:sortedOutgoingDegree.keySet()) {
		vector_out.add(st);
	}
	//if(closest_outgoing.containsKey(cu))
	//System.out.println("customer "+cu+" already in its outgoing set:"+closest_outgoing.get(cu));
	if(closest_outgoing.containsKey(cu)&&closest_outgoing.get(cu).size()==k_prime/2)
		{sortedOutgoingDegree.remove(cu);
		update_outgoing_degree(cu);
		return;
		
		}
	int count=0;
	if(closest_outgoing.containsKey(cu))
	count=closest_outgoing.get(cu).size();
	//pr.println("size:"+count);
	if(count>=k_prime/2) {
		//pr.println("esco");
		sortedOutgoingDegree.remove(cu);
		update_outgoing_degree(cu);
		return;
	}
	boolean esci=false;
	int count1=0;
	for(customer c1:prob_outgoing_aus.get(cu)) {
		//pr.println("ingoing customers for "+c1.id+":");
		if(closest_ingoing.containsKey(c1.id)&&closest_ingoing.get(c1.id).size()<k_prime/2) {
			if(closest_outgoing.containsKey(cu)&&!closest_outgoing.get(cu).contains(c1.id))
			count1++;
			else
				if(!closest_outgoing.containsKey(cu))
					count1++;
			if(count1==(k_prime/2-count)) {
				esci=true;
			}
		}
		/*if(closest_ingoing.containsKey(c1.id)) {
			for(String st:closest_ingoing.get(c1.id)) {
				pr.print(st+"\t");
			}
			
		}
		pr.println();*/
	}
//	pr.println(esci+"\t"+count1+"\t"+(k_prime/2-count));
	
	if(!esci||(k_prime/2-count)==1) {
		random_extract_outgoing_plus(cu);
		return;
	}
	ArrayList<String> cust=new ArrayList<String>();
	//pr.println("size:"+prob_outgoing_aus.get(cu).size());
	while(count<k_prime/2) {
		double v=gen_ingoing.nextDouble();
		//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
		while(v==1) {
			v=gen_ingoing.nextDouble();
			//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
			}
		double sum=0;
		
			for(int j=0;j<prob_outgoing_aus.get(cu).size();j++) {
				//pr.println("v"+"\t"+v+"\t"+"sum"+"\t"+sum);
				if(v>=sum&&v<sum+prob_outgoing_aus.get(cu).get(j).prob) {
					if(closest_outgoing.containsKey(cu)) {
						if(!closest_outgoing.get(cu).contains(prob_outgoing_aus.get(cu).get(j).id)&&count<k_prime/2) {
					if(closest_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id)&&
							closest_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).size()<k_prime/2) {

						closest_outgoing.get(cu).add(prob_outgoing_aus.get(cu).get(j).id);
						closest_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).add(cu);
					//pr.println("selected :"+prob_outgoing_aus.get(cu).get(j).id);
						if(random_outgoing.containsKey(cu))
							random_outgoing.get(cu).add(prob_outgoing_aus.get(cu).get(j).id);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(prob_outgoing_aus.get(cu).get(j).id);
							random_outgoing.put(cu, aus);
						}
						if(random_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id))
							random_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).add(cu);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(cu);
							random_ingoing.put(prob_outgoing_aus.get(cu).get(j).id, aus);
						}
						count++;
							
					}
					else {
						if(!closest_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id)) {

							ArrayList<String> cust2=new ArrayList<String>();
							cust2.add(cu);
							closest_outgoing.get(cu).add(prob_outgoing_aus.get(cu).get(j).id);
							closest_ingoing.put(prob_outgoing_aus.get(cu).get(j).id, cust2);
						//pr.println("selected :"+prob_outgoing_aus.get(cu).get(j).id);

							if(random_outgoing.containsKey(cu))
								random_outgoing.get(cu).add(prob_outgoing_aus.get(cu).get(j).id);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(prob_outgoing_aus.get(cu).get(j).id);
								random_outgoing.put(cu, aus);
							}
							if(random_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id))
								random_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).add(cu);
							else {
								ArrayList<String> aus=new ArrayList<String>();
								aus.add(cu);
								random_ingoing.put(prob_outgoing_aus.get(cu).get(j).id, aus);
							}
							count++;
							
						}
					}
						}
						
					}
					else {
						if(count<k_prime/2) {
						
						if(closest_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id)&&
								closest_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).size()<k_prime/2) {

							cust.add(prob_outgoing_aus.get(cu).get(j).id);
							closest_outgoing.put(cu, cust);
						closest_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).add(cu);
						//pr.println("selected :"+prob_outgoing_aus.get(cu).get(j).id);

						if(random_outgoing.containsKey(cu))
							random_outgoing.get(cu).add(prob_outgoing_aus.get(cu).get(j).id);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(prob_outgoing_aus.get(cu).get(j).id);
							random_outgoing.put(cu, aus);
						}
						if(random_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id))
							random_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).add(cu);
						else {
							ArrayList<String> aus=new ArrayList<String>();
							aus.add(cu);
							random_ingoing.put(prob_outgoing_aus.get(cu).get(j).id, aus);
						}
						count++;
							
						}
						else {
							if(!closest_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id)) {

								ArrayList<String> cust2=new ArrayList<String>();
								cust2.add(cu);
								cust.add(prob_outgoing_aus.get(cu).get(j).id);
								closest_outgoing.put(cu, cust);
								closest_ingoing.put(prob_outgoing_aus.get(cu).get(j).id, cust2);
							//pr.println("selected :"+prob_outgoing_aus.get(cu).get(j).id);

								if(random_outgoing.containsKey(cu))
									random_outgoing.get(cu).add(prob_outgoing_aus.get(cu).get(j).id);
								else {
									ArrayList<String> aus=new ArrayList<String>();
									aus.add(prob_outgoing_aus.get(cu).get(j).id);
									random_outgoing.put(cu, aus);
								}
								if(random_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id))
									random_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).add(cu);
								else {
									ArrayList<String> aus=new ArrayList<String>();
									aus.add(cu);
									random_ingoing.put(prob_outgoing_aus.get(cu).get(j).id, aus);
								}
								count++;
								
							}
						}
					}
				}
				}
				else {
					sum+=prob_outgoing_aus.get(cu).get(j).prob;
					//pr.println("sum:"+sum);
				}
				
			}
			

	}
	//pr.println("outgoing customers compatible for:"+cu+"\t"+"selected:"+closest_outgoing.get(cu));
	sortedOutgoingDegree.remove(cu);
	update_outgoing_degree(cu);
	

	


}
public void random_extract_outgoing() throws UnknownObjectException, IloException {
	ArrayList<String> vector_cu=new ArrayList<String> ();
	int i=0;
	for(String st:sortedOutgoingDegree.keySet()) {
		vector_cu.add(st);
	}
	String cu=vector_cu.get(i);
	while(true) {
		if(closest_outgoing.containsKey(cu)&&closest_outgoing.get(cu).size()==k_prime/2)continue;
		int count=0;
		if(closest_outgoing.containsKey(cu))
		count=closest_outgoing.get(cu).size();
		ArrayList<String> cust=new ArrayList<String>();
		while(count<k_prime/2) {
			double v=gen_ingoing.nextDouble();
			//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
			while(v==1) {
				v=gen_ingoing.nextDouble();
				//ImprovedHybridKernelSearchOneGenerator.RandTracker.println(v+";"+v);
				}
			double sum=0;
				for(int j=0;j<prob_outgoing_aus.get(cu).size();j++) {
					if(v>=sum&&v<sum+prob_outgoing_aus.get(cu).get(j).prob) {
						if(closest_outgoing.containsKey(cu)) {
							if(!closest_outgoing.get(cu).contains(prob_outgoing_aus.get(cu).get(j).id)&&count<k_prime/2) {
						if(closest_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id)&&
								closest_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).size()<k_prime/2) {

							closest_outgoing.get(cu).add(prob_outgoing_aus.get(cu).get(j).id);
							closest_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).add(cu);
							count++;
							break;
								
						}
						else {
							if(!closest_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id)) {

								ArrayList<String> cust2=new ArrayList<String>();
								cust2.add(cu);
								closest_outgoing.get(cu).add(prob_outgoing_aus.get(cu).get(j).id);
								closest_ingoing.put(prob_outgoing_aus.get(cu).get(j).id, cust2);
								count++;
								break;
								
							}
						}
							}
							else break;
						}
						else {
							if(count<k_prime/2) {
							
							if(closest_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id)&&
									closest_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).size()<k_prime/2) {

								cust.add(prob_outgoing_aus.get(cu).get(j).id);
								closest_outgoing.put(cu, cust);
							closest_ingoing.get(prob_outgoing_aus.get(cu).get(j).id).add(cu);
							count++;
							break;
								
							}
							else {
								if(!closest_ingoing.containsKey(prob_outgoing_aus.get(cu).get(j).id)) {

									ArrayList<String> cust2=new ArrayList<String>();
									cust2.add(cu);
									cust.add(prob_outgoing_aus.get(cu).get(j).id);
									closest_outgoing.put(cu, cust);
									closest_ingoing.put(prob_outgoing_aus.get(cu).get(j).id, cust2);
									count++;
									break;
									
								}
							}
						}
					}
					}
					else
						sum+=prob_outgoing_aus.get(cu).get(j).prob;
				}
				

		}
		//pr.println("outgoing customers compatible for:"+cu+"\t"+"selected:"+closest_outgoing.get(cu));
		sortedOutgoingDegree.remove(cu);
		update_outgoing_degree(cu);
		if(sortedOutgoingDegree.size()==0)break;
		vector_cu=new ArrayList<String> ();
		i=0;
		for(String st:sortedOutgoingDegree.keySet()) {
			vector_cu.add(st);
		}
		cu=vector_cu.get(i);
	}
	

}
public String find_last_customer(String id, ArrayList<String> cust) {
	
	String last=null;
	double dist=Double.NEGATIVE_INFINITY;
	for(int i=0;i<cust.size();i++)
		if(!cust.get(i).equals(id)) {
			if(dist<M.D.get(id).get(cust.get(i)))
			{
				dist=M.D.get(id).get(cust.get(i));
				last=cust.get(i);
			}
		}
	
	return last;
}
public ArrayList<customer> compute_probability_outgoing(int id){
	ArrayList<String> p=new ArrayList<String>();
	ArrayList<customer> cust=new ArrayList<customer>();
	ArrayList<customer> res=new ArrayList<customer>();
	String last=null;
	if(!arcs_out.containsKey(M.N[id].id))return res;
	for(String st:arcs_out.get(M.N[id].id))
	{
		customer c=new customer();
		c.id=st;
		c.dist=M.D.get(st).get(M.N[id].id);
		//c.prob=1.0;
		cust.add(c);
	}
	//if(degree_outgoing.get(M.N[id].id)<=k_prime/2)return cust;

	Collections.sort(cust);
	double dist=Double.NEGATIVE_INFINITY;
	double demand=0;
	for(int i=0;i<cust.size();i++)
	{
		p.add(cust.get(i).id);
		if(M.D.get(M.N[id].id).get(cust.get(i).id)>dist) {
			dist=M.D.get(M.N[id].id).get(cust.get(i).id);
			last=cust.get(i).id;
			demand=cust.get(i).load;
		}
		if(p.size()==k)break;
		
	}
	double sum=0;
	for(customer c:cust)
		if(!c.id.equals(last)&&!c.id.equals(M.N[id].id))
		{
		
			sum+=(M.D.get(M.N[id].id).get(last)/M.D.get(M.N[id].id).get(c.id));
			
		}
	sum+=1;
	customer c=new customer();
	c.prob=1/sum;
	c.id=last;
	c.dist=M.D.get(M.N[id].id).get(last);
	//pr.println("last customer:"+last+"\t"+c.dist);
	res.add(c);
	for(customer c2:cust) {
		if(!c2.id.equals(last)&&!c2.id.equals(M.N[id].id)) {
			customer c1=new customer();
			c1.prob=res.get(0).prob*M.D.get(M.N[id].id).get(last)/M.D.get(M.N[id].id).get(c2.id);
			c1.id=c2.id;
			c1.dist=M.D.get(M.N[id].id).get(c2.id);
			res.add(c1);
			
		}
	}
	return res;

}
public ArrayList<customer> compute_probability_ingoing(int id) {
	ArrayList<String> p=new ArrayList<String>();
	ArrayList<customer> cust=new ArrayList<customer>();
	ArrayList<customer> res=new ArrayList<customer>();
	String last=null;
	if(!arcs_in.containsKey(M.N[id].id))return res;
	for(String st:arcs_in.get(M.N[id].id))
	{
		customer c=new customer();
		c.id=st;
		c.dist=M.D.get(st).get(M.N[id].id);
		//c.prob=1.0;
		cust.add(c);
	}
	//if(degree_ingoing.get(M.N[id].id)<=k_prime/2)return cust;
	
	Collections.sort(cust);
	double dist=Double.NEGATIVE_INFINITY;
	for(int i=0;i<cust.size();i++)
	{
		p.add(cust.get(i).id);
		if(M.D.get(cust.get(i).id).get(M.N[id].id)>dist) {
			dist=M.D.get(cust.get(i).id).get(M.N[id].id);
			last=cust.get(i).id;
			
		}
		if(p.size()==k)break;
		
	}
	double sum=0;
	for(customer c:cust)
		if(!c.id.equals(last)&&!c.id.equals(M.N[id].id))
		{
		
			sum+=M.D.get(last).get(M.N[id].id)/M.D.get(c.id).get(M.N[id].id);
			
		}
	sum+=1;
	customer c=new customer();
	c.prob=1/sum;
	c.id=last;
	c.dist=M.D.get(last).get(M.N[id].id);
	res.add(c);
	for(customer c2:cust) {
		if(!c2.id.equals(last)&&!c2.id.equals(M.N[id].id)) {
			customer c1=new customer();
			c1.prob=res.get(0).prob*M.D.get(last).get(M.N[id].id)/M.D.get(c2.id).get(M.N[id].id);
			c1.id=c2.id;
			c1.dist=M.D.get(c2.id).get(M.N[id].id);
			res.add(c1);
			
		}
	}
	return res;
}
/*public void closest_random_customer() {
	//for(String s:M.D.keySet())
		//for(String s1:M.D.get(s).keySet())
			//pr.println("distanza fra:"+s+" e "+s1+":"+M.D.get(s).get(s1));
	for(int i=0;i<M.N.length;i++)
		if(M.N[i].id.contains("C")) {
			double d_tot=0.0;
			//pr.println("cliente:"+M.N[i].id);
			for(String s:M.D.get(M.N[i].id).keySet())
				if(s.contains("C"))
				d_tot+=M.D.get(M.N[i].id).get(s);
			//pr.println("distanza totale"+d_tot);
			double sum=0;
			double[] prob=new double[M.N.length-1];
			String[] prob_s=new String[M.N.length-2];
			int count=M.N.length-3;
			for(int j=1;j<M.N.length;j++) {
				if(i!=j&&!M.N[j].id.contains("S")) {
					//pr.println("dist:"+M.D.get(M.N[i].id).get(M.N[j].id));
					prob[count]=M.D.get(M.N[i].id).get(M.N[j].id)/d_tot+sum;
					prob_s[count]=M.N[j].id;
					count--;
				//	pr.println("cliente:"+M.N[j].id+"\t"+M.D.get(M.N[i].id).get(M.N[j].id)+"\t"+(M.D.get(M.N[i].id).get(M.N[j].id)/d_tot+sum));
					sum+=M.D.get(M.N[i].id).get(M.N[j].id)/d_tot;
				}
			}
			for(int j=0;j<prob.length;j++)
				prob[j]=1-prob[j];
			prob[prob.length-1]=1.0;

			//for(int j=0;j<prob_s.length;j++)
				//pr.println("cliente:"+prob_s[j]+" probabilita':"+prob[j]);
			double casual=Math.random();
	//	pr.println("estratto:"+casual);
			int pos=0;
			for(int j=1;j<prob.length;j++) {
				if(casual<=prob[j])
					{
					pos=j-1;
					break;
					}
				//if(casual<=prob[j]&&casual>prob[j-1]) {
					//pos=j;
					//break;
				//}
			}
			ArrayList<String> L=new ArrayList<String>();
			L.add(prob_s[pos]);
			closest.put(M.N[i].id, L);
			pr.println("cliente:"+M.N[i].id+" scelto:"+L.get(0));
		}

}*/
/*public void random_customer() {
	for(int i=0;i<M.N.length;i++)
		if(M.N[i].id.contains("C")) {
			String[] prob_s=new String[M.N.length-2];
			int count=0;
			for(int j=1;j<M.N.length;j++) {
				if(i!=j&&!M.N[j].id.contains("S")) {
					prob_s[count]=M.N[j].id;
					count++;
				}
			}
			int casuale = (int)(Math.random()*prob_s.length);
			ArrayList<String> L=new ArrayList<String>();
			L.add(prob_s[casuale]);
			closest.put(M.N[i].id, L);
			pr.println("cliente:"+M.N[i].id+" scelto:"+L.get(0));
		}

}*/
public void set_zero_variables_outgoing() throws IloException {
	/*pr.println("Selected ingoing customers for ");
	for(String s:closest_ingoing.keySet()) {
		pr.print("customer "+s+":");
		for(String v:closest_ingoing.get(s)) {
			pr.print(v+"\t");
		}
		pr.println();
	}
	pr.println("Selected outgoing customers for ");
	for(String s:closest_outgoing.keySet()) {
		pr.print("customer "+s+":");
		for(String v:closest_outgoing.get(s)) {
			pr.print(v+"\t");
		}
		pr.println();
	}*/
	//pr.println("GRAPH (x variables not set to zero):");
	for(XIndex s:M.x.keySet()) {
		if(!s.xi.contains("D")) {

			if(!s.xj.contains("D")&&closest_outgoing.containsKey(s.xi)&&!closest_outgoing.get(s.xi).contains(s.xj)) {

				M.x.get(s).setUB(0.0);
	
			}
			else {
				//pr.println("x("+s.xi+","+s.xj+","+s.staz+")");
				
			}
		
	}
		else {


			//pr.println("x("+s.xi+","+s.xj+","+s.staz+")");
		
		
		}
	}
	
}
public void set_zero_variables_ingoing() throws IloException {
	/*pr.println("Selected customers for ");
	for(String s:closest.keySet()) {
		pr.print("customer "+s+":");
		for(String v:closest.get(s)) {
			pr.print(v+"\t");
		}
		pr.println();
	}*/
	//pr.println("GRAPH (x variables not set to zero):");
	for(XIndex s:M.x.keySet()) {
		if(!s.xi.contains("D")) {

			if(!s.xj.contains("D")&&closest_ingoing.containsKey(s.xj)&&!closest_ingoing.get(s.xj).contains(s.xi)) {

				M.x.get(s).setUB(0.0);
	
			}
			else {
				//pr.println("x("+s.xi+","+s.xj+","+s.staz+")");
				
			}
		
	}
		else {


			//pr.println("x("+s.xi+","+s.xj+","+s.staz+")");
		
		
		}
	}
	
}
public void restore_zero_variables_outgoing() throws IloException {
	for(XIndex s:M.x.keySet()) {
		if(!s.xi.contains("D"))

			if(!s.xj.contains("D")&&closest_outgoing.containsKey(s.xi)&&!closest_outgoing.get(s.xi).contains(s.xj))

				M.x.get(s).setUB(1.0);
	
		
	}
}
public void restore_zero_variables_ingoing() throws IloException {
	for(XIndex s:M.x.keySet()) {
		if(!s.xi.contains("D"))

			if(!s.xj.contains("D")&&closest_ingoing.containsKey(s.xi)&&!closest_ingoing.get(s.xi).contains(s.xj))

				M.x.get(s).setUB(1.0);
	
		
	}
}
public void increasing_degree_ingoing() throws UnknownObjectException, IloException {
	sortedIngoingDegree.clear();
	degree_ingoing.entrySet()
	    .stream()
	    .sorted(Map.Entry.comparingByValue())
	    .forEachOrdered(el -> sortedIngoingDegree.put(el.getKey(), el.getValue()));
	/*pr.println("ingoing degree:");
	for(String cu:sortedIngoingDegree.keySet())
		pr.println(cu+"\t"+sortedIngoingDegree.get(cu));
	*/
}
public void increasing_degree_outgoing() throws UnknownObjectException, IloException {
	sortedOutgoingDegree.clear();
	degree_outgoing.entrySet()
	    .stream()
	    .sorted(Map.Entry.comparingByValue())
	    .forEachOrdered(el -> sortedOutgoingDegree.put(el.getKey(), el.getValue()));
	/*pr.println("outgoing degree:");
	for(String cu:sortedOutgoingDegree.keySet())
		pr.println(cu+"\t"+sortedOutgoingDegree.get(cu));
	*/
}
public void write_output(int ite) throws FileNotFoundException, IloException {
	pr.println("solution iteration "+ite);
	if(M.model.getStatus().equals(IloCplex.Status.Infeasible))
	{
		pr.println("Model Status:"+M.model.getStatus());
		return;
	}
	pr.println("objective function without service times at customers:"+M.model.getObjValue());
	double obj=M.model.getObjValue();
	for(int i=1;i<M.N.length;i++)
		obj+=M.FD*M.N[i].service_time;
	pr.println("objective function with service times at customers:"+obj);
	pr.println("solution status:"+M.model.getStatus());
	pr.println("x variables");
	for(XIndex na:M.x.keySet()) {
		if(M.model.getValue(M.x.get(na))>=0.99)
			pr.println("customer "+na.xi+" customer "+na.xj+" station "+na.staz);
	}

}
public void reset() {
	for(String cu:random_ingoing.keySet()) {
		ArrayList<String> aus=new ArrayList<String>();
		//pr.println("per il cliente "+cu+" dopo aver risolto il modello:"+closest_ingoing.get(cu));
		for(int i=0;i<closest_ingoing.get(cu).size();i++) {
			if(!random_ingoing.get(cu).contains(closest_ingoing.get(cu).get(i))) {
				aus.add(closest_ingoing.get(cu).get(i));
			}
		}
		closest_ingoing.put(cu, aus);
		//pr.println("per il cliente "+ cu+" prima di risolvere un nuovo modello:"+closest_ingoing.get(cu));
			
	}
	random_ingoing.clear();
	for(String cu:random_outgoing.keySet()) {
		ArrayList<String> aus=new ArrayList<String>();
		//pr.println("per il cliente "+cu+" dopo aver risolto il modello:"+closest_outgoing.get(cu));

		for(int i=0;i<closest_outgoing.get(cu).size();i++) {
			if(!random_outgoing.get(cu).contains(closest_outgoing.get(cu).get(i))) {
				aus.add(closest_outgoing.get(cu).get(i));
			}
		}
		closest_outgoing.put(cu, aus);
		//pr.println("per il cliente "+cu+" prima di risolvere un nuovo modello:"+closest_outgoing.get(cu));

	}
	random_outgoing.clear();
}
public void copy() throws UnknownObjectException, IloException {
	for(String cu:degree_ingoing.keySet()) {
		degree_ingoing_aus.put(cu, degree_ingoing.get(cu));
	}
	for(String cu:degree_outgoing.keySet()) {
		degree_outgoing_aus.put(cu, degree_outgoing.get(cu));
	}
	for(String cu:prob_ingoing_aus.keySet())
		prob_ingoing_aus_1.put(cu, prob_ingoing_aus.get(cu));
	for(String cu:prob_outgoing_aus.keySet())
		prob_outgoing_aus_1.put(cu, prob_outgoing_aus.get(cu));
}
public void copy_opposite() throws UnknownObjectException, IloException {

	for(String cu:degree_ingoing_aus.keySet()) {
		degree_ingoing.put(cu, degree_ingoing_aus.get(cu));
	}
	for(String cu:degree_outgoing_aus.keySet()) {
		degree_outgoing.put(cu, degree_outgoing_aus.get(cu));
	}
	increasing_degree_ingoing();
	increasing_degree_outgoing();
	random_ingoing.clear();
	random_outgoing.clear();
	prob_outgoing_aus.clear();
	prob_ingoing_aus.clear();
	for(String cu:prob_ingoing_aus_1.keySet())
		prob_ingoing_aus.put(cu, prob_ingoing_aus_1.get(cu));
	for(String cu:prob_outgoing_aus_1.keySet())
		prob_outgoing_aus.put(cu, prob_outgoing_aus_1.get(cu));

}
//
//public static void main(String[] args) throws IOException, IloException {
//
//	double time_start=System.currentTimeMillis();
//	int count_infeasibility=0;
//	int count_optimal=0;
//	double current_objective=Double.POSITIVE_INFINITY;
//	double optimum_objective=Double.POSITIVE_INFINITY;	
//	double current_objective_with_service_time=Double.POSITIVE_INFINITY;
//	double optimum_objective_with_service_time=Double.POSITIVE_INFINITY;
//	double time_to_best=0;
//	ArrayList<String> current_best_solution=new ArrayList<String>();
//	ArrayList<String> best_solution=new ArrayList<String>();
//	int best_iteration=0;
//	int best_k_prime=0;
//	int num_iteration_no_improved=5;//Integer.parseInt(args[13]);
//	int count_iteration_no_improved=0;
//	String current_best_status="";
//	int count_run=0;
//	boolean changed=false;
//	
//	InstanceReaderGeneratorTech ir = new InstanceReaderGeneratorTech();
//	ir.generate(args);//il metodo di InstanceReaderGeneratorTech che prende in input il file dell'instanza
//	matheuristic_k_degree_hybridOneGenerator_tech mat=new matheuristic_k_degree_hybridOneGenerator_tech(args);
//	String name=args[1].replace(".txt", "");
//
//	mat.M.R = new	RechargeTypes[ir.Inst.NumTechs];
//	for (int tec = 0; tec<ir.Inst.NumTechs;tec++){
//		mat.M.R[tec] = new RechargeTypes();
//		mat.M.R[tec].id=Integer.parseInt(ir.Inst.TechId[tec]);
//		mat.M.R[tec].description="Tec_"+tec;
//		mat.M.R[tec].cost = ir.Inst.EnCost[tec];
//		mat.M.R[tec].speed = ir.Inst.RecSpeed[tec];
//
//		if(ir.Inst.RecSpeed[tec] < mat.M.speed_min)// se la velocità di ricarica è < del minimo
//			mat.M.speed_min = ir.Inst.RecSpeed[tec];
//	}
///*
// * Ciclo for che:
// * elabora dati all'interno di un'HashMap RSTech e crea una nuova 
// * struttura dati RRT contenente oggetti RechargeTypes associati 
// * a chiavi specifiche, basati su alcune condizioni e dati presenti 
// * in oggetti dell'oggetto ir.Inst.
// */
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
//		mat.M.RRT.put(stat,Rlist);
//	}
//	
//	
//	mat.M.generate_nodes(ir);
//	
//	//************* START cRandRemove *************
//	
//	/*
//	mat.M.printN0();
//	
//	mat.M.cRandRemove();
//	
//	mat.M.printN0();
//	*/
//	
//	//*************  END  cRandRemove *************	
//	
//	//************* START sRandRemove *************
//	
//	/*	
//	mat.M.printRS();
//	mat.M.sRandRemove();
//	mat.M.printRS();
//	*/
//	
//	//*************  END  sRandRemove *************	
//
//	
//	mat.M.generate_distances();
//	mat.M.init_parameters(ir);
//	mat.M.compute_S();
//	mat.M.initVariables_Ok();
//	mat.M.initModel(mat.M);
//	mat.feasible_arcs();
//	mat.M.timeLimitMIPiter= 30;
//	mat.num_infeasibility = 2;
//	
//	
//	
//	
//	//considera codice fin qui 
//	
//	while(count_iteration_no_improved<num_iteration_no_improved) {//num max=5
//	changed=false;
//
//	//NEW CODE
//	
//	/*
//	System.out.println("**** CHECK NODES N0 INIZIALI ****");
//	mat.M.printN0();
//	mat.M.cRandRemove();
//	System.out.println("**** CHECK NODES N0 POST cRandRemove ****");
//	mat.M.printN0();
//
//	*/
//	//END NEW CODE
//
//
//	
//	for(String st:mat.degree_in.keySet()) {
//		mat.degree_ingoing.put(st, mat.degree_in.get(st));
//	}
//	for(String st:mat.degree_out.keySet()) {
//		mat.degree_outgoing.put(st, mat.degree_out.get(st));
//	}
//		System.out.println("** deg out size:"+mat.degree_outgoing.size()+" deging size:"+mat.degree_ingoing.size());
//		System.out.println("** deg out:"+mat.degree_outgoing+" deging:"+mat.degree_ingoing);
//	mat.pr.println("k':"+mat.k_prime);
//	mat.compute_start_ingoing_degree();
//	mat.compute_start_outgoing_degree();
//	
//	mat.probabilities();
//	
//	mat.increasing_degree_ingoing();
//	mat.increasing_degree_outgoing();
//
//	mat.copy();
//	int counter=0;
//	while(!changed) {
//		
//		//NEW CODE
//		
//
//
//		
//		//END NEW CODE
//		System.out.println("*******COUNTER*******"+counter);
//		counter++;
//	System.err.println("k':"+mat.k_prime);
//		System.out.println("deg in:"+mat.prob_ingoing);
//		System.out.println("deg out:"+mat.prob_outgoing);
//	mat.random_extract();
//		System.out.println("deg out:"+mat.random_outgoing.size()+" deging:"+mat.random_ingoing.size());
//	mat.set_zero_variables_outgoing();
//	
//	mat.M.solve(name+"_output_matheuristic"+mat.k_prime+".txt");
//	//mat.write_output(count_run);
//	if((mat.M.model.getStatus().equals(IloCplex.Status.Infeasible)||mat.M.model.getStatus().equals(IloCplex.Status.Unknown))&&count_infeasibility>=mat.num_infeasibility) {
//		mat.k_prime+=2;
//		if(mat.k_prime/2>mat.k)break;
//		mat.pr.println("Reached the maximum number of consecutive infeasible iterations. New value for k prime:"+mat.k_prime);
//		changed=true;
//		count_run=0;
//		count_infeasibility=0;
//		count_optimal=0;
//	}
//	else {
//		if((mat.M.model.getStatus().equals(IloCplex.Status.Infeasible)||mat.M.model.getStatus().equals(IloCplex.Status.Unknown))&&count_infeasibility<mat.num_infeasibility) {
//			count_run++;
//			count_infeasibility++;
//			mat.pr.println("Again an infeasible: iteration"+count_infeasibility+ " with k':"+mat.k_prime);
//			if(count_run==mat.num_run)break;
//
//		}
//		else {
//			if((mat.M.model.getStatus().equals(IloCplex.Status.Optimal)||mat.M.model.getStatus().equals(IloCplex.Status.Feasible))&&current_objective<=mat.M.model.getObjValue()&&count_optimal>=mat.num_optimal) {
//				mat.k_prime+=2;
//				if(mat.k_prime/2>mat.k)break;
//				mat.pr.println("Reached the maximum number of consecutive iterations with the same incumbent. New value for k prime:"+mat.k_prime);
//				changed=true;
//				count_run=0;
//				count_infeasibility=0;
//				count_optimal=0;
//			}
//			else {
//				if((mat.M.model.getStatus().equals(IloCplex.Status.Optimal)||mat.M.model.getStatus().equals(IloCplex.Status.Feasible))&&current_objective<=mat.M.model.getObjValue()&&count_optimal<mat.num_optimal) {
//					count_run++;
//					count_optimal++;
//					count_infeasibility=0;
//					mat.pr.println("Again the same optimal solution:"+count_optimal+ " with k':"+mat.k_prime);
//					if(count_run==mat.num_run)break;
//
//
//				}
//				else {
//					if((mat.M.model.getStatus().equals(IloCplex.Status.Optimal)||mat.M.model.getStatus().equals(IloCplex.Status.Feasible))&&current_objective-mat.M.model.getObjValue()>Math.pow(10, -6)) {
//						count_run++;
//						count_optimal=0;
//						count_infeasibility=0;
//						current_objective=mat.M.model.getObjValue();
//						current_best_solution.clear();
//						best_iteration=count_run-1;
//						best_k_prime=mat.k_prime;
//						time_to_best=(System.currentTimeMillis()-time_start)/1000.00;
//
//						double obj=current_objective;
//						for(int i=1;i<mat.M.N.length;i++)
//							obj+=mat.M.FD*mat.M.N[i].service_time;
//						current_objective_with_service_time=obj;
//						current_best_status=mat.M.model.getStatus().toString();
//						for(XIndex na:mat.M.x.keySet()) {
//							if(mat.M.model.getValue(mat.M.x.get(na))>=0.99)
//								current_best_solution.add("customer "+na.xi+" customer "+na.xj+" station "+na.staz);
//						}
//
//						mat.pr.println("New incubent:"+current_objective+" obtained with k prime="+mat.k_prime+" after "+time_to_best+" seconds");
//						System.err.println("New incubent:"+current_objective+" obtained with k prime="+mat.k_prime+" after "+time_to_best+" seconds");
//
//						if(count_run==mat.num_run)break;
//
//
//							}
//					}
//				
//			}
//		}
//	}
//	mat.restore_zero_variables_outgoing();
//	mat.reset();
//	mat.copy_opposite();
//	System.out.println("Run: "+count_run);
//	}
//	if(current_objective<optimum_objective) {
//		optimum_objective=current_objective;
//		optimum_objective_with_service_time=current_objective_with_service_time;
//		best_solution.clear();
//		for(String st:current_best_solution) {
//			best_solution.add(st);
//		}
//		count_iteration_no_improved=0;
//	}
//	else
//		if(current_objective>optimum_objective)
//			count_iteration_no_improved=0;
//		else
//			count_iteration_no_improved++;
//	if(mat.k_prime/2>=mat.k)break;
//	}
//	mat.pr.println("Condition to end:");
//	if(count_iteration_no_improved==num_iteration_no_improved)
//		mat.pr.println("Reached the maximum number of consecutive iterations without improving the objective");
//	else
//		mat.pr.println("Reached k'/2 "+mat.k_prime/2+" and k "+mat.k);
//	time_start=(System.currentTimeMillis()-time_start)/1000.00;
//	mat.pr.println("Time to solve:"+time_start+ " seconds");
//	mat.pr.println("Best solution obtained at run "+best_iteration+" with k'="+best_k_prime+ " obtained after "+time_to_best+ " seconds");
//	mat.pr.println("Status:"+current_best_status);
//	mat.pr.println("Objective function:"+current_objective);
//	mat.pr.println("Objective function with service times:"+current_objective_with_service_time);
//	mat.pr.println("Routing variables equal to 1:");
//	for(String st:current_best_solution)
//		mat.pr.println(st);
//	mat.pr.close();
//}

}
