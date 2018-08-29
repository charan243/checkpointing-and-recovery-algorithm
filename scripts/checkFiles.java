import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class checkFiles {

    public static class VectorClock{
        private int[] clock;

        public VectorClock(int numberofrows){
            clock = new int[numberofrows];
        }
    }

    public static boolean incomparable(int[] a, int[] b, int nodeID1, int nodeID2){
        if (a[nodeID1]>=b[nodeID1] && b[nodeID2]>=a[nodeID2])
            return true;

        return false;
    }

    public static void main (String args[]){
        int numberofnodes = Integer.parseInt(args[0]);
        int numberoffailures = Integer.parseInt(args[1]);

        VectorClock[][] globalstate = new VectorClock[numberoffailures][numberofnodes];

        for (int i=0; i<numberofnodes; i++){
            String filename = i + ".out";
            try {
                BufferedReader br = new BufferedReader(new FileReader(filename));
                String in;

                for (int j=0; j<numberoffailures; j++) {
                    in = br.readLine();

                    // populate a vector clock with the read in line
                    String[] temp = in.split(" ");
                    VectorClock a = new VectorClock(numberofnodes);
                    for (int k=0; k<numberofnodes; k++){
                        a.clock[k] = Integer.parseInt(temp[k]);
                    }

                    globalstate[j][i] = a;
                }

                br.close();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }

        }

        // the matrix is populated and ready for comparisons
	boolean success=true;
        for (int i=0; i<numberoffailures; i++){
            for (int j=0; j<numberofnodes-1; j++){
                for (int k=j+1; k<numberofnodes; k++){
                    //compare globalstate[i][j] with globalstate[i][k]

                    if (!incomparable(globalstate[i][j].clock, globalstate[i][k].clock, j, k)) {
                        success=false;
			System.out.println("Failure" + i + "caused an inconsistent global state");
                    }
                }
            }
        }

	if (success){
		System.out.println("There was no inconsistent global state");
	}

    }


}
