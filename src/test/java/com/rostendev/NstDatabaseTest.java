package com.rostendev;


import com.rostendev.database.NstDatabase;
import org.junit.Test;

import java.io.IOException;

public class NstDatabaseTest {

    @Test
    public void crearBaseDeDatos() throws IOException {

        NstDatabase db = NstDatabase.open("personas");

    }
}
