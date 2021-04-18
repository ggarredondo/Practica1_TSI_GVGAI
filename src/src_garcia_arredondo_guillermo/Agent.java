package src_garcia_arredondo_guillermo;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

public class Agent extends AbstractPlayer {
    private ArrayDeque<Types.ACTIONS> secuencia;
    private Vector2d fescala;
    private int n_gemas;
    private final int max_gemas = 9;
    private Types.ACTIONS orientacion;
    private boolean hay_gemas, hay_enemigos;
    private Vector2d inicio, objetivo;
    private ArrayList<Vector2d> obstaculos, gemas;

    public Agent(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        // Inicializar n_gemas, la orientación (dada por la última acción), la pila de pasos, la escala, la posición inicio del avatar y la del portal
        n_gemas = 0;
        orientacion = Types.ACTIONS.ACTION_RIGHT;
        secuencia = new ArrayDeque<>();
        fescala = new Vector2d(stateObs.getWorldDimension().width/stateObs.getObservationGrid().length, stateObs.getWorldDimension().height/stateObs.getObservationGrid()[0].length);
        inicio = new Vector2d(Math.floor(stateObs.getAvatarPosition().x/fescala.x), Math.floor(stateObs.getAvatarPosition().y/fescala.y));
        Vector2d portal = stateObs.getPortalsPositions(stateObs.getAvatarPosition())[0].get(0).position;
        objetivo = new Vector2d(Math.floor(portal.x/fescala.x), Math.floor(portal.y/fescala.y));

        // Añadir los obstáculos del mapa a una lista de obstáculos para luego sortearlos
        obstaculos = new ArrayList<>();
        ArrayList<Observation> immovables = stateObs.getImmovablePositions(stateObs.getAvatarPosition())[0];
        for (Observation immovable : immovables)
            obstaculos.add(new Vector2d(immovable.position.x / fescala.x, immovable.position.y / fescala.y));

        // Comprobar el nivel de los 5 en el que nos encontramos
        hay_gemas = stateObs.getResourcesPositions() != null;
        hay_enemigos = stateObs.getNPCPositions() != null;
        if (!hay_gemas && !hay_enemigos)
            AEstrella(stateObs, inicio, objetivo, orientacion);
        else if (hay_gemas) {
            // Añadir las gemas del mapa a una lista de gemas para llevar la cuenta
            gemas = new ArrayList<>();
            ArrayList<Observation> gems = stateObs.getResourcesPositions()[0];
            for (Observation gem : gems)
                gemas.add(new Vector2d(gem.position.x/fescala.x, gem.position.y/fescala.y));
            Greedy(stateObs);
        }
    }

    // Para ralentizar el programa y depurar el camino obtenido
    private void sleep(long ms) {
        TimeUnit time = TimeUnit.MILLISECONDS;
        try { time.sleep(ms); }
        catch (InterruptedException e) { System.out.println("Interrupted while Sleeping"); }
    }

    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        Types.ACTIONS siguiente = Types.ACTIONS.ACTION_NIL;
        if (!secuencia.isEmpty())
            siguiente = secuencia.pollFirst();
        else if (hay_enemigos) {
            Esquivar(stateObs);
            siguiente = secuencia.pollFirst();
        }
        return siguiente;
    }

    private void Esquivar(StateObservation stateObs)
    {
        ArrayList<Observation> npcs = stateObs.getNPCPositions(new Vector2d(inicio.x*fescala.x, inicio.y*fescala.y))[0];
        ArrayList<Vector2d> enemigos = new ArrayList<>();
        for (Observation npc : npcs) enemigos.add(new Vector2d(npc.position.x / fescala.x, npc.position.y / fescala.y));
        obstaculos.addAll(enemigos);

        NodoReactivo nodo = new NodoReactivo(inicio.x, inicio.y, 0, orientacion, 0, enemigos.get(0)), escogido;
        Stack<NodoReactivo> sucesores = new Stack<>();
        PriorityQueue<NodoReactivo> abiertos = new PriorityQueue<>();

        sucesores.push(nodo.hijoUP(enemigos.get(0)));
        sucesores.push(nodo.hijoDOWN(enemigos.get(0)));
        sucesores.push(nodo.hijoLEFT(enemigos.get(0)));
        sucesores.push(nodo.hijoRIGHT(enemigos.get(0)));
        while (!sucesores.empty()) {
            nodo = sucesores.pop();
            if (!obstaculos.contains(nodo.getPos()))
                abiertos.add(nodo);
        }
        obstaculos.removeAll(enemigos);

        escogido = abiertos.peek();
        if (escogido.getAccion() != secuencia.peekLast()) {
            secuencia.clear();
            inicio = escogido.getPos();
            orientacion = escogido.getAccion();
            for (int i = 0; i < escogido.getVeces_accion(); i++)
                secuencia.addFirst(escogido.getAccion());
        }
    }

    private Vector2d getClosestGem(Vector2d start) {
        int d, min = Integer.MAX_VALUE;
        Vector2d min_v = null;
        for (Vector2d gema : gemas) {
            d = (int) (Math.abs(start.x - gema.x) + Math.abs(start.y - gema.y));
            if (d < min) {
                min = d;
                min_v = gema;
            }
        }
        return min_v;
    }

    private void Greedy(StateObservation stateObs)
    {
        ArrayDeque<Types.ACTIONS> total = new ArrayDeque<>();
        Vector2d semiobjetivo;
        while (n_gemas < max_gemas) {
            semiobjetivo = getClosestGem(inicio);
            AEstrella(stateObs, inicio, semiobjetivo, orientacion);
            total.addAll(secuencia);
            secuencia.clear();
            inicio = semiobjetivo;
            orientacion = total.peekLast();
        }
        AEstrella(stateObs, inicio, objetivo, orientacion);
        total.addAll(secuencia);
        orientacion = total.peekLast();
        secuencia = total;
    }

    private void AEstrella(StateObservation stateObs, Vector2d inicio, Vector2d objetivo, Types.ACTIONS ultima)
    {
        PriorityQueue<Nodo> abiertos = new PriorityQueue<>();
        ArrayList<Nodo> cerrados = new ArrayList<>();
        Stack<Nodo> sucesores = new Stack<>();
        Nodo actual = new Nodo(inicio.x, inicio.y, 0, ultima, null, objetivo), hijo;
        abiertos.add(actual);

        while (!abiertos.isEmpty() && !actual.esObjetivo()) {
            abiertos.remove(actual);
            cerrados.add(actual);
            sucesores.push(actual.hijoUP(objetivo));
            sucesores.push(actual.hijoDOWN(objetivo));
            sucesores.push(actual.hijoLEFT(objetivo));
            sucesores.push(actual.hijoRIGHT(objetivo));

            while (!sucesores.empty()) {
                hijo = sucesores.pop();
                if (!obstaculos.contains(hijo.getPos()) && !cerrados.contains(hijo))
                    abiertos.add(hijo);
            }

            actual = abiertos.peek();
        }
        if (actual.esObjetivo()) {
            while (actual.getPadre() != null) {
                secuencia.addFirst(actual.getAccion());
                if (hay_gemas) {
                    if (gemas.contains(actual.getPos())) {
                        gemas.remove(actual.getPos());
                        n_gemas++;
                    }
                }
                actual = actual.getPadre();
            }
        }
    }
}
