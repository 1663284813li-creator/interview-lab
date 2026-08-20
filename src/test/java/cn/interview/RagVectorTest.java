package cn.interview;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagVectorTest {
    @Test void enforcesEmbeddingSchemaBeforeDatabaseInsertion() {
        assertThrows(IllegalStateException.class,()->RagService.vector(new float[1536]));
        float[] bad=new float[1024];bad[0]=Float.NaN;
        assertThrows(IllegalStateException.class,()->RagService.vector(bad));
        assertEquals(1024,RagService.vector(new float[1024]).substring(1).split(",").length);
    }
}
