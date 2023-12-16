
public class node {
String id;
double demand, service_time, s, e, x, y;
String type; //c=customer, d=depot
public float getX() {
    return (float) this.x;
}
public float getDemand() {
    return (float) this.demand;
}

public float getY() {
    return (float) this.y;
}
@Override
public String toString() {
	return this.id;
}

}
