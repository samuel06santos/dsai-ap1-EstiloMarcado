package br.ufpa.dsai.estilomarcado.autenticacao.model;

import java.time.Instant;

import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalizado", nullable = false, length = 254, unique = true)
    private String emailNormalizado;

    @Column(name = "senha_hash", length = 100)
    private String senhaHash;

    @Column(name = "firebase_uid", length = 128, unique = true)
    private String firebaseUid;

    @Column(name = "senha_firebase", nullable = false)
    private boolean senhaFirebase;

    @Column(name = "telefone_contato", length = 20)
    private String telefoneContato;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PerfilUsuario perfil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoConta estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id", unique = true)
    private Profissional profissional;

    @Column(name = "tentativas_login", nullable = false)
    private int tentativasLogin;

    @Column(name = "primeira_falha_em")
    private Instant primeiraFalhaEm;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "senha_alterada_em")
    private Instant senhaAlteradaEm;

    protected Usuario() {
    }

    public Usuario(String nome, String email, String emailNormalizado, String senhaHash,
                   PerfilUsuario perfil, EstadoConta estado, Unidade unidade, Profissional profissional) {
        this.nome = nome;
        this.email = email;
        this.emailNormalizado = emailNormalizado;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.estado = estado;
        this.unidade = unidade;
        this.profissional = profissional;
        this.senhaAlteradaEm = senhaHash == null ? null : Instant.now();
    }

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    public void registrarFalha(Instant agora) {
        if (primeiraFalhaEm == null || primeiraFalhaEm.isBefore(agora.minusSeconds(15 * 60L))) {
            primeiraFalhaEm = agora;
            tentativasLogin = 1;
        } else {
            tentativasLogin++;
        }
        if (tentativasLogin >= 5) {
            estado = EstadoConta.BLOQUEADA;
            bloqueadoAte = agora.plusSeconds(15 * 60L);
        }
    }

    public void desbloquearSeExpirado(Instant agora) {
        if (estado == EstadoConta.BLOQUEADA && bloqueadoAte != null && !bloqueadoAte.isAfter(agora)) {
            estado = EstadoConta.ATIVA;
            limparFalhas();
        }
    }

    public void limparFalhas() {
        tentativasLogin = 0;
        primeiraFalhaEm = null;
        bloqueadoAte = null;
    }

    public void trocarSenha(String novoHash) {
        senhaHash = novoHash;
        senhaAlteradaEm = Instant.now();
        limparFalhas();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public String getEmailNormalizado() { return emailNormalizado; }
    public String getSenhaHash() { return senhaHash; }
    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String firebaseUid) { this.firebaseUid = firebaseUid; }
    public boolean isSenhaFirebase() { return senhaFirebase; }
    public void setSenhaFirebase(boolean senhaFirebase) { this.senhaFirebase = senhaFirebase; }
    public String getTelefoneContato() { return telefoneContato; }
    public void setTelefoneContato(String telefoneContato) { this.telefoneContato = telefoneContato; }
    public PerfilUsuario getPerfil() { return perfil; }
    public void setPerfil(PerfilUsuario perfil) { this.perfil = perfil; }
    public EstadoConta getEstado() { return estado; }
    public void setEstado(EstadoConta estado) { this.estado = estado; }
    public Unidade getUnidade() { return unidade; }
    public void setUnidade(Unidade unidade) { this.unidade = unidade; }
    public Profissional getProfissional() { return profissional; }
    public void setProfissional(Profissional profissional) { this.profissional = profissional; }
    public int getTentativasLogin() { return tentativasLogin; }
    public Instant getPrimeiraFalhaEm() { return primeiraFalhaEm; }
    public Instant getBloqueadoAte() { return bloqueadoAte; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public Instant getSenhaAlteradaEm() { return senhaAlteradaEm; }
}
