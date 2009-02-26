package v2.state;

import v2.data.BitBoard;
import v2.data.BitLoc;
import v2.piece.Colour;
import v2.piece.Figure;
import v2.piece.Piece;

/**
 * Date: Feb 6, 2009
 * Time: 2:07:25 AM
 *
 * NOTE: can only undo ONE move, after that the state is undefined.
 */
public class State
{
    //--------------------------------------------------------------------
    private static final byte WHITE_K_CASTLE = 1;
    private static final byte WHITE_Q_CASTLE = 1 << 1;
    private static final byte BLACK_K_CASTLE = 1 << 2;
    private static final byte BLACK_Q_CASTLE = 1 << 3;

    private static final byte WHITE_CASTLE = WHITE_K_CASTLE |
                                             WHITE_Q_CASTLE;
    private static final byte BLACK_CASTLE = BLACK_K_CASTLE |
                                             BLACK_Q_CASTLE;

//    private static final byte BLACK_CASTLE_SHIFT = 2;


    //--------------------------------------------------------------------
    private static final int PAWNS   = Figure.PAWN  .ordinal();
    private static final int KNIGHTS = Figure.KNIGHT.ordinal();
    private static final int BISHOPS = Figure.BISHOP.ordinal();
    private static final int ROOKS   = Figure.ROOK  .ordinal();
    private static final int QUEENS  = Figure.QUEEN .ordinal();
    private static final int KING    = Figure.KING  .ordinal();


    //--------------------------------------------------------------------
    private long[] wPieces;
    private long[] bPieces;

    private long whiteBB;
    private long blackBB;

    private byte enPassants; // avaiable to take for nextToAct

    private byte castles;
    private byte prevCastles;

    private byte reversibleMoves;
    private byte prevReversibleMoves;

    private Colour nextToAct;


    //--------------------------------------------------------------------
    public State()
    {
        wPieces = new long[ Figure.VALUES.length ];
        bPieces = new long[ Figure.VALUES.length ];

        for (int p = 0; p < 8; p++) {
            wPieces[ PAWNS ] |=  BitLoc.locationToBitBoard(1, p);
            bPieces[ PAWNS ] |=  BitLoc.locationToBitBoard(6, p);
        }

        wPieces[ ROOKS ] = BitLoc.locationToBitBoard(0, 0) |
                           BitLoc.locationToBitBoard(0, 7);
        bPieces[ ROOKS ] = BitLoc.locationToBitBoard(7, 0) |
                           BitLoc.locationToBitBoard(7, 7);

        wPieces[ KNIGHTS ] = BitLoc.locationToBitBoard(0, 1) |
                             BitLoc.locationToBitBoard(0, 6);
        bPieces[ KNIGHTS ] = BitLoc.locationToBitBoard(7, 1) |
                             BitLoc.locationToBitBoard(7, 6);

        wPieces[ BISHOPS ] = BitLoc.locationToBitBoard(0, 2) |
                             BitLoc.locationToBitBoard(0, 5);
        bPieces[ BISHOPS ] = BitLoc.locationToBitBoard(7, 2) |
                             BitLoc.locationToBitBoard(7, 5);

        wPieces[ QUEENS ] = BitLoc.locationToBitBoard(0, 3);
        bPieces[ QUEENS ] = BitLoc.locationToBitBoard(7, 3);

        wPieces[ KING ] = BitLoc.locationToBitBoard(0, 4);
        bPieces[ KING ] = BitLoc.locationToBitBoard(7, 4);

        enPassants = 0;
        castles    = WHITE_CASTLE | BLACK_CASTLE;

        reversibleMoves = 0;
        nextToAct       = Colour.WHITE;

        for (Figure f : Figure.VALUES) {
            whiteBB |= wPieces[ f.ordinal() ];
            blackBB |= bPieces[ f.ordinal() ];
        }

        prevCastles         = castles;
        prevReversibleMoves = reversibleMoves;
    }

    private State(long[] copyWPieces,
                  long[] copyBPieces,
                  byte   copyEnPassants,
                  byte   copyCastles,
                  byte   copyReversibleMoves,
                  Colour copyNextToAct,
                  long   copyWhiteBB,
                  long   copyBlackBB,
                  byte   copyPrevCastles,
                  byte   copyPrevReversibleMoves)
    {
        wPieces = copyWPieces;
        bPieces = copyBPieces;

        enPassants      = copyEnPassants;
        castles         = copyCastles;
        reversibleMoves = copyReversibleMoves;
        nextToAct       = copyNextToAct;

        whiteBB = copyWhiteBB;
        blackBB = copyBlackBB;

        prevCastles         = copyPrevCastles;
        prevReversibleMoves = copyPrevReversibleMoves;
    }


    //--------------------------------------------------------------------
    /**
     * generate all pseudo-legal moves from this position
     *
     * @param moves generate moves into
     * @return number of moves generated, or -1 if mate is possible
     */
    public int moves(int[] moves)
    {
        long occupied      = whiteBB | blackBB;
        long notOccupied   = ~occupied;

        long proponent, opponent, oppKing, pieces[];
        if (nextToAct == Colour.WHITE) {
            proponent = whiteBB;
            opponent  = blackBB;
            oppKing   = bPieces[ KING ];
            pieces    = wPieces;
        } else {
            proponent = blackBB;
            opponent  = whiteBB;
            oppKing   = wPieces[ KING ];
            pieces    = bPieces;
        }
        long notProponent = ~proponent;
        long notOpponent  = ~opponent;

        int offset = 0;
        for (Figure f : Figure.VALUES)
        {
            long  bb = pieces[ f.ordinal() ];
            while (bb != 0)
            {
                long pieceBoard  = BitBoard.lowestOneBit(bb);
                long pseudoMoves = Piece.valueOf(nextToAct, f).moves(
                        pieceBoard, occupied, notOccupied,
                        proponent, notProponent, opponent);
                if ((oppKing & pseudoMoves) != 0) return -1;

                offset = addMoves(
                        f, pieceBoard, moves, offset,
                        pseudoMoves, opponent, notOpponent);

                // reset LS1B
                bb &= bb - 1;
            }
        }
        return offset;
    }
    
    private int addMoves(
            Figure figure,
            long   fromBB,
            int[]  moves,
            int    offset,
            long   movesBB,
            long   opponent,
            long   notOpponent)
    {
        int from = BitLoc.bitBoardToLocation(fromBB);
        int off = addMobility(
                figure, from, moves, offset, movesBB & notOpponent);
        return addCaptures(
                figure, from, moves, off, movesBB & opponent);
    }

    private int addMobility(
            Figure figure,
            int    from,
            int[]  moves,
            int    offset,
            long   moveBB)
    {
        if (moveBB == 0) return offset;
        while (moveBB != 0)
        {
            long moveBoard = BitBoard.lowestOneBit(moveBB);
            moves[ offset++ ] = Move.mobility(//nextToAct,
                    figure, from, BitLoc.bitBoardToLocation(moveBoard));
            moveBB &= moveBB - 1;
        }
        return offset;
    }

    private int addCaptures(
            Figure figure,
            int    from,
            int[]  moves,
            int    offset,
            long   moveBB)
    {
        if (moveBB == 0) return offset;
        while (moveBB != 0)
        {
            long moveBoard = BitBoard.lowestOneBit(moveBB);
            moves[ offset++ ] = Move.capture(//nextToAct,
                    figure, from, BitLoc.bitBoardToLocation(moveBoard));
            moveBB &= moveBB - 1;
        }
        return offset;
    }


    //--------------------------------------------------------------------
    public void unMobalize(
            Figure figure,
            int    fromSquareIndex,
            int    toSquareIndex)
    {
        mobalize(nextToAct.invert(), figure,
                 BitLoc.locationToBitBoard(fromSquareIndex),
                 BitLoc.locationToBitBoard(toSquareIndex));

        castles         = prevCastles;
        reversibleMoves = prevReversibleMoves;
    }


    //--------------------------------------------------------------------
    public void mobalize(
            Figure figure,
            int    fromSquareIndex,
            int    toSquareIndex)
    {
        prevCastles         = castles;
        prevReversibleMoves = reversibleMoves;
        doMobalize(figure, fromSquareIndex, toSquareIndex);
    }
    private void doMobalize(
            Figure  figure,
            int     fromSquareIndex,
            int     toSquareIndex)
    {
        mobalize(nextToAct, figure,
                 BitLoc.locationToBitBoard(fromSquareIndex),
                 BitLoc.locationToBitBoard(toSquareIndex));

        updateCasltingRights(
                figure, fromSquareIndex);

        if (figure == Figure.PAWN) {
            reversibleMoves = 0;
        } else {
            reversibleMoves++;
        }
    }
    private void mobalize(
            Colour colour,
            Figure figure,
            long   from,
            long   to)
    {
        long fromTo = from ^ to;

        if (colour == Colour.WHITE) {
            wPieces[ figure.ordinal() ] ^= fromTo;
            whiteBB ^= fromTo;
        } else {
            bPieces[ figure.ordinal() ] ^= fromTo;
            blackBB ^= fromTo;
        }

        nextToAct = nextToAct.invert();
    }

    private void updateCasltingRights(
            Figure mover, int from)
    {
        if (castles == 0) return;
        if (mover == Figure.KING) {
            castles &= ~((nextToAct == Colour.WHITE)
                         ? WHITE_CASTLE : BLACK_CASTLE);
        } else if (mover == Figure.ROOK) {
            if (nextToAct == Colour.WHITE) {
                if (from == 0) {
                    castles &= ~WHITE_Q_CASTLE;
                } else if (from == 7) {
                    castles &= ~WHITE_K_CASTLE;
                }
            } else {
                if (from == 56) {
                    castles &= ~BLACK_Q_CASTLE;
                } else if (from == 7) {
                    castles &= ~BLACK_K_CASTLE;
                }
            }
        }
    }


    //--------------------------------------------------------------------
    public void unCapture(
            Figure attacker,
            Figure captured,
            int    fromSquareIndex,
            int    toSquareIndex)
    {
        long from   = BitLoc.locationToBitBoard(fromSquareIndex);
        long to     = BitLoc.locationToBitBoard(  toSquareIndex);
        long fromTo = from ^ to;

        if (nextToAct == Colour.WHITE) {
            // black is the attacher
            bPieces[ attacker.ordinal() ] ^= fromTo;
            wPieces[ captured.ordinal() ] ^= to;

            blackBB ^= fromTo;
            whiteBB ^= to;
        } else {
            wPieces[ attacker.ordinal() ] ^= fromTo;
            bPieces[ captured.ordinal() ] ^= to;

            whiteBB ^= fromTo;
            blackBB ^= to;
        }

        nextToAct       = nextToAct.invert();
        castles         = prevCastles;
        reversibleMoves = prevReversibleMoves;
    }


    //--------------------------------------------------------------------
    public Figure capture(
            Figure attacker,
            int    fromSquareIndex,
            int    toSquareIndex)
    {
        long toBB = BitLoc.locationToBitBoard(toSquareIndex);
        Figure captured = figureAt(toBB, nextToAct.invert());
        capture(attacker.ordinal(), captured.ordinal(),
                BitLoc.locationToBitBoard(fromSquareIndex),
                BitLoc.locationToBitBoard(toSquareIndex));

        prevCastles = castles;
        updateCasltingRights(
                attacker, fromSquareIndex);
        prevReversibleMoves = reversibleMoves;
        reversibleMoves     = 0;
        return captured;
    }
    private void capture(
            int  attacker,
            int  captured,
            long from,
            long to)
    {
        long fromTo = from ^ to;

        if (nextToAct == Colour.WHITE) {
            wPieces[attacker] ^= fromTo;
            bPieces[captured] ^= to;

            whiteBB ^= fromTo;
            blackBB ^= to;
        } else {
            bPieces[attacker] ^= fromTo;
            wPieces[captured] ^= to;

            blackBB ^= fromTo;
            whiteBB ^= to;
        }

        nextToAct = nextToAct.invert();
    }


    //--------------------------------------------------------------------
    public boolean isInCheck(Colour colour)
    {
        long   occupied    = whiteBB | blackBB;
        long   notOccupied = ~occupied;

        long attacker, attacked, targetKing, attackingPieces[];
        if (colour == Colour.BLACK) {
            attacker        = whiteBB;
            attacked        = blackBB;
            targetKing      = bPieces[ KING ];
            attackingPieces = wPieces;
        } else {
            attacker        = blackBB;
            attacked        = whiteBB;
            targetKing      = wPieces[ KING ];
            attackingPieces = bPieces;
        }
        long notProponent = ~attacker;

        Colour attackColour = colour.invert();
        for (Figure f : Figure.VALUES)
        {
            Piece p  = Piece.valueOf(attackColour, f);
            long  bb = attackingPieces[ f.ordinal() ];
            while (bb != 0)
            {
                long pieceBoard  = BitBoard.lowestOneBit(bb);
                long pseudoMoves = p.moves(
                        pieceBoard, occupied, notOccupied,
                        attacker, notProponent, attacked);
                if ((targetKing & pseudoMoves) != 0) return true;
                bb &= bb - 1;
            }
        }
        return false;
    }

    public Colour nextToAct()
    {
        return nextToAct;
    }


    //--------------------------------------------------------------------
    // see http://chessprogramming.wikispaces.com/Draw+evaluation
    // can later be substituted with tablebase
    public Status knownStatus()
    {
        if (reversibleMoves > 100) return Status.DRAW;

        // no major pieces
        if (wPieces[ ROOKS  ] != 0 ||
            bPieces[ ROOKS  ] != 0 ||
            wPieces[ QUEENS ] != 0 ||
            bPieces[ QUEENS ] != 0) return Status.IN_PROGRESS;

        boolean whiteBishops, blackBishops;
        boolean whiteKnights, blackKnights;

        boolean whitePawns = (wPieces[ PAWNS ] != 0);
        boolean blackPawns = (bPieces[ PAWNS ] != 0);
        if (whitePawns && blackPawns) {
            return Status.IN_PROGRESS;
        } else {
            whiteBishops = (wPieces[ BISHOPS ] != 0);
            blackBishops = (bPieces[ BISHOPS ] != 0);

            whiteKnights = (wPieces[ KNIGHTS ] != 0);
            blackKnights = (bPieces[ KNIGHTS ] != 0);

            if (whitePawns || blackPawns) {
                // at least one side has at least a minor pawn
                if (whiteBishops || blackBishops ||
                    whiteKnights || blackKnights) {
                    return Status.IN_PROGRESS;
                } else {
                    if (whitePawns) {
                        int nWhitePawns =
                                Long.bitCount(wPieces[ PAWNS ]);
                        return (nWhitePawns == 1)
                               ? Status.DRAW : Status.IN_PROGRESS;
                    } else {
                        int nBlackPawns =
                                Long.bitCount(bPieces[ PAWNS ]);
                        return (nBlackPawns == 1)
                               ? Status.DRAW : Status.IN_PROGRESS;
                    }
                }
            }
        }
        // no pawns

        if (whiteBishops && blackBishops) {
            if (whiteKnights || blackKnights){
                return Status.IN_PROGRESS;
            }

            // both sides have a king and a bishop,
            //   the bishops being the same color
            int nWhiteBishops = Long.bitCount(wPieces[ BISHOPS ]);
            if (nWhiteBishops > 1) return Status.IN_PROGRESS;

            int nBlackBishops = Long.bitCount(wPieces[ BISHOPS ]);
            if (nBlackBishops > 1) return Status.IN_PROGRESS;

            return (BitBoard.isDark(wPieces[ BISHOPS ]) ==
                    BitBoard.isDark(bPieces[ BISHOPS ]))
                   ? Status.DRAW : Status.IN_PROGRESS;
        }
        else if (whiteBishops || blackBishops)
        {
            // one player has a bishop
            return (whiteKnights || blackKnights)
                   ? Status.IN_PROGRESS
                   : Status.DRAW;
        }
        // no bishops

        if (whiteKnights && blackKnights) return Status.IN_PROGRESS;
        if (whiteKnights) {
            int nWhiteKnights =
                    Long.bitCount(wPieces[ KNIGHTS ]);

            //one side has two knights against the bare king
            return (nWhiteKnights <= 2)
                    ? Status.DRAW : Status.IN_PROGRESS;
        } else if (blackKnights) {
            int nBlackKnights =
                    Long.bitCount(bPieces[ KNIGHTS ]);
            return (nBlackKnights <= 2)
                    ? Status.DRAW : Status.IN_PROGRESS;
        }
        return Status.DRAW;
    }


    //--------------------------------------------------------------------
    private Piece pieceAt(int rankIndex, int fileIndex)
    {
        long loc = BitLoc.locationToBitBoard(rankIndex, fileIndex);
        for (Figure f : Figure.VALUES) {
            if ((wPieces[ f.ordinal() ] & loc) != 0)
                return Piece.valueOf(Colour.WHITE, f);

            if ((bPieces[ f.ordinal() ] & loc) != 0)
                return Piece.valueOf(Colour.BLACK, f);
        }
        return null;
    }

    private Figure figureAt(long location, Colour ofColour)
    {
        long[] pieces = (ofColour == Colour.WHITE)
                        ? wPieces : bPieces;

        for (Figure f : Figure.VALUES)
        {
            long occupied = pieces[ f.ordinal() ];
            if ((occupied & location) != 0) return f;
        }
        return null;
    }


    //--------------------------------------------------------------------
    public State prototype()
    {
        return new State(wPieces.clone(), bPieces.clone(),
                         enPassants,
                         castles,
                         reversibleMoves,
                         nextToAct,
                         whiteBB, blackBB,
                         prevCastles, prevReversibleMoves);
    }


    public boolean checkPieces()
    {
        return whiteBB == calcPieces(Colour.WHITE) &&
               blackBB == calcPieces(Colour.BLACK);
    }
    public long calcPieces(Colour c)
    {
        long[] pieces = (c == Colour.WHITE)
                        ? wPieces : bPieces;

        long bb = 0;
        for (Figure f : Figure.VALUES)
        {
            bb |= pieces[ f.ordinal() ];
        }
        return bb;
    }


    //--------------------------------------------------------------------
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("Reversible Moves: ").append(reversibleMoves);

        str.append("\nCastles Available: ");
        if (castles == 0) {
            str.append("none");
        } else {
            if ((castles & WHITE_CASTLE) != 0) {
                str.append("[white: ");
                if ((castles & WHITE_CASTLE) == WHITE_CASTLE) {
                    str.append("O-O, O-O-O");
                } else if ((castles & WHITE_Q_CASTLE) != 0) {
                    str.append("O-O-O");
                } else {
                    str.append("O-O");
                }
                str.append("] ");
            }
            if ((castles & BLACK_CASTLE) != 0) {
                str.append("[black: ");
                if ((castles & BLACK_CASTLE) == BLACK_CASTLE) {
                    str.append("O-O, O-O-O");
                } else if ((castles & BLACK_Q_CASTLE) != 0) {
                    str.append("O-O-O");
                } else {
                    str.append("O-O");
                }
                str.append("]");
            }
        }

        str.append("\nEn Passants: ");
        if (enPassants == 0) {
            str.append("none");
        } else {
//            if (whiteEnPassants != 0) {
//                str.append("white ")
//                   .append(Long.lowestOneBit(whiteEnPassants))
//                   .append(" ");
//            }
//            if (blackEnPassants != 0) {
//                str.append("black ")
//                   .append(Long.lowestOneBit(blackEnPassants));
//            }
        }

        for (int rank = 7; rank >= 0; rank--)
        {
            str.append("\n");
            for (int file = 0; file < 8; file++)
            {
                Piece p = pieceAt(rank, file);
                str.append((p == null) ? " " : p);
            }
        }

        return str.toString();
    }
}