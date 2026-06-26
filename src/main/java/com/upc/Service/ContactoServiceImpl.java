package com.upc.Service;

import com.upc.DTO.ContactoBloqueDTO;
import com.upc.DTO.ContactoCierreDTO;
import com.upc.Entity.ContactoBloque;
import com.upc.Entity.ContactoCierre;
import com.upc.Repository.ContactoBloqueRepository;
import com.upc.Repository.ContactoCierreRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContactoServiceImpl implements ContactoService {

    @Autowired
    private ContactoBloqueRepository contactoBloqueRepository;

    @Autowired
    private ContactoCierreRepository contactoCierreRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    @Transactional
    public List<ContactoBloqueDTO> obtenerBloques() {
        if (contactoBloqueRepository.count() == 0) {
            inicializarBloques();
        }
        return contactoBloqueRepository.findAll().stream()
                .map(block -> modelMapper.map(block, ContactoBloqueDTO.class))
                .collect(Collectors.toList());
    }

    private void inicializarBloques() {
        List<ContactoBloque> defaultBlocks = new ArrayList<>();
        defaultBlocks.add(new ContactoBloque("block-wa", "WhatsApp", "Escríbenos y te respondemos lo antes posible.", "whatsapp", "Escríbenos por WhatsApp", "https://wa.me/51999999999", true));
        defaultBlocks.add(new ContactoBloque("block-ig", "Instagram", "Síguenos y descubre nuestras novedades.", "instagram", "@Brandname", "https://instagram.com/", true));
        defaultBlocks.add(new ContactoBloque("block-support", "Atención al Cliente", "Estamos para ayudarte en lo que necesites.", "support", "Lun - Sáb: 9:00 am - 6:00 pm", "", true));
        defaultBlocks.add(new ContactoBloque("block-email", "Email", "Escríbenos y te responderemos lo antes posible.", "email", "hola@brandname.com", "mailto:hola@brandname.com", true));
        defaultBlocks.add(new ContactoBloque("block-info", "Información", "Resolvemos tus dudas sobre productos, pedidos, envíos y más.", "info", "", "", true));
        contactoBloqueRepository.saveAll(defaultBlocks);
    }

    @Override
    @Transactional
    public ContactoBloqueDTO actualizarBloque(String id, ContactoBloqueDTO dto) {
        ContactoBloque bloque = contactoBloqueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bloque de contacto no encontrado con id: " + id));
        bloque.setTitle(dto.getTitle());
        bloque.setDescription(dto.getDescription());
        bloque.setBtnText(dto.getBtnText());
        bloque.setBtnLink(dto.getBtnLink());
        bloque.setVisible(dto.getVisible());
        ContactoBloque guardado = contactoBloqueRepository.save(bloque);
        return modelMapper.map(guardado, ContactoBloqueDTO.class);
    }

    @Override
    @Transactional
    public ContactoCierreDTO obtenerCierre() {
        if (contactoCierreRepository.count() == 0) {
            inicializarCierre();
        }
        List<ContactoCierre> cierres = contactoCierreRepository.findAll();
        return modelMapper.map(cierres.get(0), ContactoCierreDTO.class);
    }

    private void inicializarCierre() {
        ContactoCierre cierre = new ContactoCierre();
        cierre.setBtnText("¿Dudas sobre tu pedido?");
        cierre.setNumber("51999999999");
        cierre.setMessage("Hola, tengo una consulta sobre un pedido.");
        cierre.setVisible(true);
        contactoCierreRepository.save(cierre);
    }

    @Override
    @Transactional
    public ContactoCierreDTO actualizarCierre(ContactoCierreDTO dto) {
        if (contactoCierreRepository.count() == 0) {
            inicializarCierre();
        }
        ContactoCierre cierre = contactoCierreRepository.findAll().get(0);
        cierre.setBtnText(dto.getBtnText());
        cierre.setNumber(dto.getNumber());
        cierre.setMessage(dto.getMessage());
        cierre.setVisible(dto.getVisible());
        ContactoCierre guardado = contactoCierreRepository.save(cierre);
        return modelMapper.map(guardado, ContactoCierreDTO.class);
    }
}
