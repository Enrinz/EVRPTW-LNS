public class IJind {

    public final String xi;
    public final String xj;

    public IJind(final String i, final String j)
    {
        this.xi = i;
        this.xj = j;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result  + this.xi.hashCode();
        result = prime*result + this.xj.hashCode();
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
        final IJind other = (IJind) obj;
        if (xi.equals(other.xi) && xj.equals(other.xj))
            return true;
        return false;
    }

}
