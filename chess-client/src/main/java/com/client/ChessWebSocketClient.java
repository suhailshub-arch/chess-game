// package com.client;

// import java.net.URI;
// import java.net.URISyntaxException;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Scanner;

// import org.java_websocket.client.WebSocketClient;
// import org.java_websocket.handshake.ServerHandshake;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.shared.dto.Envelope;
// import com.shared.dto.HeartbeatAckDTO;
// import com.shared.dto.JoinMessageDTO;
// import com.shared.dto.MoveBroadcastDTO;
// import com.shared.dto.OpponentReconnectedDTO;
// import com.shared.dto.PauseDTO;
// import com.shared.dto.ResumeOkDTO;

// public class ChessWebSocketClient extends WebSocketClient{

//     private String PLAYERID_TEST;
//     private ObjectMapper objectMapper;
    
//     public ChessWebSocketClient(URI serverURI, String playerId){
//         super(serverURI);
//         this.PLAYERID_TEST = playerId;
//         this.objectMapper = new ObjectMapper();
//     }

//     @Override
//     public void onOpen(ServerHandshake handshakeData){
//         JoinMessageDTO joinMsg = new JoinMessageDTO(PLAYERID_TEST, "Alice", 1500);
//         Envelope<JoinMessageDTO> joinEnvelope = new Envelope<>("join", joinMsg);
//         try {
//             String messageJson = objectMapper.writeValueAsString(joinEnvelope);
//             send(messageJson);
//         } catch (Exception e) {
//             System.err.println(e);
//         }
//         new Thread(() -> {
//             Scanner scanner = new Scanner(System.in);
//             while (true) {
//                 String line = scanner.nextLine();
//                 send(line);
//             }
//         }).start();

//     }

//     @Override
//     public void onClose(int code, String reason, boolean remote){
//         System.out.println("closed with exit code " + code + " additional info: " + reason);
//     }

//     @Override
//     public void onMessage(String message){
//         // System.out.println("Received message: " + message);
//         try {
//             JsonNode root = objectMapper.readTree(message);
//             String messageType = root.get("type").asText();

//             if ("move".equals(messageType)) {
//                 MoveBroadcastDTO moveBroadcastDTO = objectMapper.treeToValue(root.get("payload"), MoveBroadcastDTO.class);
//                 List<List<String>> board = fenToBoard(moveBroadcastDTO.fen());
//                 printBoard(board);
//             }
//             if("heartbeat".equals(messageType)){
//                 long ts = root.get("payload").get("ts").asLong();
//                 Envelope<HeartbeatAckDTO> ackEnvelope = new Envelope<>("heartbeat_ack", new HeartbeatAckDTO(ts));
//                 String json = objectMapper.writeValueAsString(ackEnvelope);
//                 send(json);
//                 // System.out.printf("[LOG] Heartbeat from Server ts=%d%n", ts);
//             }
//             if ("pause".equals(messageType)) {
//                 PauseDTO pauseDTO = objectMapper.treeToValue(root.get("payload"), PauseDTO.class);
//                 System.out.printf("[LOG] Game %d paused by %s. Must resume by %d%n", pauseDTO.gameId(), pauseDTO.disconnectedPlayerId(), pauseDTO.resumeDeadlineMillis());
//             }
//             if ("resumeOk".equals(messageType)) {
//                 ResumeOkDTO resumeOkDTO = objectMapper.treeToValue(root.get("payload"), ResumeOkDTO.class);
//                 System.out.printf("[LOG] Game %d resumed%n", resumeOkDTO.gameId());
//             }
//             if ("opponentReconnected".equals(messageType)) {
//                 OpponentReconnectedDTO opponentReconnectedDTO = objectMapper.treeToValue(root.get("payload"), OpponentReconnectedDTO.class);
//                 System.out.printf("[LOG] Opponent %s reconnected to game %d%n", opponentReconnectedDTO.playerId(), opponentReconnectedDTO.gameId());
//             }
//         } catch (Exception e) {
//             // TODO: handle exception
//         }
//     }

//     @Override
//     public void onError(Exception ex){
//         System.err.println("Error occured: " + ex.getMessage());
//     }

//     public static List<List<String>> fenToBoard(String fen) {
//         List<List<String>> board = new ArrayList<>();
//         // Only take the board part of the FEN string
//         String[] rows = fen.split(" ")[0].split("/");

//         for (String row : rows) {
//             List<String> brow = new ArrayList<>();
//             for (int i = 0; i < row.length(); i++) {
//                 char c = row.charAt(i);
//                 if (Character.isDigit(c)) {
//                     int empty = c - '0';
//                     for (int j = 0; j < empty; j++) {
//                         brow.add("--");
//                     }
//                 } else if (c == 'p') {
//                     brow.add("bp");
//                 } else if (c == 'P') {
//                     brow.add("wp");
//                 } else if (Character.isLowerCase(c)) {
//                     // black piece
//                     brow.add("b" + Character.toUpperCase(c));
//                 } else {
//                     // white piece
//                     brow.add("w" + c);
//                 }
//             }
//             board.add(brow);
//         }
//         return board;
//     }

//     public static void printBoard(List<List<String>> board) {
//         System.out.println("   a  b  c  d  e  f  g  h");
//         int rowNum = 8;
//         for (List<String> row : board) {
//             System.out.print(rowNum + " ");
//             for (String s : row) {
//                 System.out.print(s + " ");
//             }
//             System.out.println();
//             rowNum--;
//         }
//     }

//     public static void main(String[] args) throws URISyntaxException, InterruptedException{
//         String playerId = args[0];
//         WebSocketClient client = new ChessWebSocketClient(new URI("ws://localhost:8080"), playerId);
//         client.connect();
       
//         Thread.currentThread().join();
//     }
// }

package com.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.shared.dto.Envelope;
import com.shared.dto.HeartbeatAckDTO;
import com.shared.dto.JoinMessageDTO;
import com.shared.dto.MatchedMessageDTO;
import com.shared.dto.MoveBroadcastDTO;
import com.shared.dto.MoveMessageDTO;
import com.shared.dto.OpponentDTO;
import com.shared.dto.OpponentReconnectedDTO;
import com.shared.dto.PauseDTO;
import com.shared.dto.ResumeOkDTO;
import com.shared.dto.GameOverDTO;
import com.shared.util.Colour;

public class ChessWebSocketClient extends WebSocketClient {

    // ---------- config ----------
    private static final boolean PRINT_DEBUG_JSON = false;
    private static final Pattern UCI_RE = Pattern.compile("^[a-h][1-8][a-h][1-8][qrbnQRBN]?$");

    // glyph mode (auto-detect, overridable via --ascii / --unicode)
    private static boolean USE_UNICODE_PIECES = detectUnicodePieces();

    // ---------- json ----------
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- identity ----------
    private final String playerId;
    private final String playerName;
    private final int playerRating;

    // ---------- client state (for render) ----------
    private volatile long gameId = -1L;
    private volatile Colour yourColour = null;
    private volatile OpponentDTO opponent = null;
    private volatile String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private volatile Colour toPlay = Colour.WHITE;
    private volatile boolean paused = false;
    private final Deque<String> lastMoves = new ArrayDeque<>(8);

    // ---------- ansi helpers ----------
    private static final String CSI = "\u001b[";

    private static boolean ansiEnabled() {
        String term = System.getenv("TERM");
        return term != null || System.console() != null;
    }
    private static void clearScreen() {
        if (ansiEnabled()) System.out.print(CSI + "2J" + CSI + "H");
        else for (int i = 0; i < 60; i++) System.out.println();
    }
    private static void hideCursor() { if (ansiEnabled()) System.out.print(CSI + "?25l"); }
    private static void showCursor() { if (ansiEnabled()) System.out.print(CSI + "?25h"); }

    public ChessWebSocketClient(URI serverURI, String playerId) {
        this(serverURI, playerId, "Alice", 1500);
    }

    public ChessWebSocketClient(URI serverURI, String playerId, String name, int rating) {
        super(serverURI);
        this.playerId = playerId;
        this.playerName = name;
        this.playerRating = rating;
    }

    // ---------- websocket callbacks ----------
    @Override
    public void onOpen(ServerHandshake handshakeData) {
        // send join
        try {
            send(objectMapper.writeValueAsString(new Envelope<>("join",
                new JoinMessageDTO(playerId, playerName, playerRating))));
        } catch (Exception e) {
            System.err.println("[CLIENT] Failed to send join: " + e.getMessage());
        }
        new Thread(this::inputLoop, "input-loop").start();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        showCursor();
        System.out.printf("[CLIENT] Closed (%d): %s%n", code, reason);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[CLIENT] Error: " + ex.getMessage());
    }

    @Override
    public void onMessage(String message) {
        if (message == null) return;
        String trimmed = message.trim();
        if (trimmed.isEmpty()) return;

        // ignore non-JSON frames (e.g., "Welcome to the server")
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            if (PRINT_DEBUG_JSON) System.out.println("<< NON-JSON: " + trimmed);
            return;
        }

        if (PRINT_DEBUG_JSON) System.out.println("<< " + trimmed);
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            String type = root.get("type").asText();
            JsonNode payload = root.get("payload");

            switch (type) {
                case "matchFound" -> {
                    MatchedMessageDTO m = objectMapper.treeToValue(payload, MatchedMessageDTO.class);
                    this.gameId = m.gameId();
                    this.yourColour = m.colour();
                    this.opponent = m.opponent();
                    this.fen = m.initialFen();
                    this.toPlay = Colour.WHITE;
                    this.paused = false;
                    render();
                }
                case "move" -> {
                    MoveBroadcastDTO b = objectMapper.treeToValue(payload, MoveBroadcastDTO.class);
                    this.fen = b.fen();
                    this.toPlay = b.toPlay();
                    pushMove(b.uci());
                    render();
                }
                case "pause" -> {
                    PauseDTO p = objectMapper.treeToValue(payload, PauseDTO.class);
                    if (this.gameId == p.gameId()) {
                        this.paused = true;
                        render();
                    }
                }
                case "resumeOk" -> {
                    ResumeOkDTO ok = objectMapper.treeToValue(payload, ResumeOkDTO.class);
                    if (this.gameId <= 0L) this.gameId = ok.gameId();
                    this.fen = ok.fen();
                    this.toPlay = ok.toPlay();
                    this.paused = false;
                    render();
                }
                case "opponentReconnected" -> {
                    OpponentReconnectedDTO or = objectMapper.treeToValue(payload, OpponentReconnectedDTO.class);
                    if (this.gameId == or.gameId()) {
                        this.paused = false;
                        render();
                    }
                }
                case "heartbeat" -> {
                    long ts = payload.get("ts").asLong();
                    Envelope<HeartbeatAckDTO> ack = new Envelope<>("heartbeat_ack", new HeartbeatAckDTO(ts));
                    send(objectMapper.writeValueAsString(ack));
                }
                case "error" -> {
                    String code = payload.get("code").asText();
                    String msg = payload.get("message").asText();
                    System.out.printf("%n[ERROR] %s: %s%n", code, msg);
                    render();
                }
                case "gameOver" -> {
                    GameOverDTO over = objectMapper.treeToValue(payload, GameOverDTO.class);
                    System.out.printf(
                        "%n[GAME OVER] game=%d result=%s reason=%s winner=%s%n",
                        over.gameId(), over.result(), over.reason(),
                        over.winnerId() == null ? "-" : over.winnerId()
                    );
                    render();
                }
                default -> {
                    // ignore unknown
                }
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] onMessage parse error: " + e.getMessage());
        }
    }

    // ---------- input & send ----------
    private void inputLoop() {
        Scanner sc = new Scanner(System.in);
        try {
            while (true) {
                System.out.print("uci> ");
                String line = sc.nextLine();
                if (line == null) continue;
                line = line.trim();
                if (line.isEmpty()) continue;

                if ("exit".equalsIgnoreCase(line)) { close(); break; }
                if ("help".equalsIgnoreCase(line)) { printHelp(); continue; }
                if ("fen".equalsIgnoreCase(line))  { System.out.println(this.fen); continue; }

                // also accept "move e2e4"
                String uci = line.startsWith("move ") ? line.substring(5).trim() : line;
                if (UCI_RE.matcher(uci).matches()) sendMove(uci);
                else System.out.println("Unrecognized input. Try: e2e4  (or 'help')");
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] input error: " + e.getMessage());
        } finally {
            showCursor();
        }
    }

    private void sendMove(String uci) {
        if (gameId <= 0 || yourColour == null) {
            System.out.println("Not in a game yet.");
            return;
        }
        if (paused) {
            System.out.println("Game is paused; cannot move.");
            return;
        }
        try {
            send(objectMapper.writeValueAsString(
                new Envelope<>("move", new MoveMessageDTO(gameId, playerId, uci))));
        } catch (Exception e) {
            System.err.println("[CLIENT] failed to send move: " + e.getMessage());
        }
    }

    private void printHelp() {
        System.out.println("""
            Commands:
              e2e4           Make a move in UCI (supports promotions like e7e8q)
              move e2e4      Same as above
              fen            Print current FEN
              help           This help
              exit           Quit the client
            """);
    }

    // ---------- rendering ----------
    private synchronized void render() {
        clearScreen();
        hideCursor();

        String status = paused ? "⏸ paused — waiting for opponent"
                : (toPlay == yourColour ? "Your move" : "Their move");

        String opp = (opponent == null) ? "-" : (opponent.name() + "(" + opponent.rating() + ")");
        String you = (yourColour == null) ? "-" : yourColour.name();
        String sep = USE_UNICODE_PIECES ? " • " : " - ";

        String header = String.format("Game #%s  You: %s  vs %s%s%s%n",
                (gameId <= 0 ? "-" : String.valueOf(gameId)), you, opp, sep, status);

        StringBuilder sb = new StringBuilder(1024);
        sb.append(header).append("   a  b  c  d  e  f  g  h\n");

        // board from FEN
        String[] rows = fen.split(" ")[0].split("/");
        int rank = 8;
        for (String row : rows) {
            sb.append(rank).append(' ');
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (Character.isDigit(c)) {
                    int n = c - '0';
                    for (int k = 0; k < n; k++) sb.append(" . ");
                } else {
                    sb.append(' ').append(toGlyph(c)).append(' ');
                }
            }
            sb.append(' ').append(rank).append('\n');
            rank--;
        }
        sb.append("   a  b  c  d  e  f  g  h\n\n");

        sb.append("Moves: ");
        if (lastMoves.isEmpty()) sb.append("(none)");
        else for (String m : lastMoves) sb.append(m).append(' ');
        sb.append('\n');

        System.out.print(sb.toString());
        showCursor();
    }

    private void pushMove(String uci) {
        if (lastMoves.size() == 8) lastMoves.removeFirst();
        lastMoves.addLast(uci);
    }

    // ---------- glyphs ----------
    private static boolean detectUnicodePieces() {
        // Conservative: Windows consoles often lack these unless configured
        String os = System.getProperty("os.name", "").toLowerCase();
        return !(os.contains("win"));
    }

    private static char toGlyph(char fenPiece) {
        if (USE_UNICODE_PIECES) return toUnicode(fenPiece);
        // ASCII fallback: use FEN letters
        if (Character.isLetter(fenPiece)) return fenPiece;
        return '.';
    }

    private static char toUnicode(char c) {
        return switch (c) {
            case 'K' -> '♔'; case 'Q' -> '♕'; case 'R' -> '♖';
            case 'B' -> '♗'; case 'N' -> '♘'; case 'P' -> '♙';
            case 'k' -> '♚'; case 'q' -> '♛'; case 'r' -> '♜';
            case 'b' -> '♝'; case 'n' -> '♞'; case 'p' -> '♟';
            default  -> '.';
        };
    }

    // ---------- main ----------
    public static void main(String[] args) throws URISyntaxException, InterruptedException {
        if (args.length < 1) {
            System.out.println("Usage: java ... ChessWebSocketClient <PLAYER_ID> [ws://host:port] [--ascii|--unicode]");
            return;
        }
        String pid = args[0];
        String url = (args.length >= 2 && !args[1].startsWith("--")) ? args[1] : "ws://localhost:8080";

        // parse flags
        for (String a : args) {
            if ("--ascii".equalsIgnoreCase(a))   USE_UNICODE_PIECES = false;
            if ("--unicode".equalsIgnoreCase(a)) USE_UNICODE_PIECES = true;
        }

        // carry pid for future sticky sessions
        if (!url.contains("?")) url = url + "?pid=" + pid;

        WebSocketClient client = new ChessWebSocketClient(new URI(url), pid);
        client.connect();

        Thread.currentThread().join();
    }
}

