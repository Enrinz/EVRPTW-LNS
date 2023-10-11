

public class XIndex {
    //HashMap<String, HashMap<String, IloNumVar[]>>
    public final String xi;
    public final String xj;
    public final String staz;
    //public int xInds;

    public XIndex(final String i, final String j, final String s)
    {
        this.xi = i;
        this.xj = j;
        this.staz = s;
    }
    @Override
    public int hashCode() {
        final int prime = 31;

        int result = 1;;
        result = prime * result + this.staz.hashCode();
        result  = result*prime + this.xi.hashCode();
        result = result*prime +this.xj.hashCode();
        return result;
    }
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final XIndex other = (XIndex) obj;
        if (xi.equals(other.xi) && xj.equals(other.xj) && staz.equals(other.staz))
            return true;
        return false;
    }

}
